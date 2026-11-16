package com.c0dex.wincli

import com.c0dex.core.crypto.Argon2Kdf
import com.c0dex.core.crypto.KeyHierarchy
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.KdfConfig
import com.c0dex.core.format.RecordPayload
import com.c0dex.core.format.VaultContainer
import com.c0dex.core.generator.PasswordGenerator
import com.c0dex.core.merge.VaultMerger
import com.c0dex.core.util.TimeFormat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

private fun usage() = println(
    """
    passvault <command> [args]
      create <file>                 создать vault (мастер спросит дважды)
      list   <file>                 список записей
      add    <file> <desc> [pass]   добавить (pass omitted -> спросит; "gen" -> сгенерировать)
      show   <file> <id>            показать запись
      del    <file> <id>            удалить (tombstone)
      merge  <file> <other>         слить конфликт-копию в file
      gen    [length]               сгенерировать пароль (default 16)
    """.trimIndent()
)

private fun master(prompt: String): CharArray {
    val console = System.console()
    if (console != null) return console.readPassword(prompt)
    print(prompt)
    return (BufferedReader(InputStreamReader(System.`in`)).readLine() ?: "").toCharArray()
}

private fun load(file: Path, master: CharArray): VaultContainer? {
    val c = try {
        ContainerCodec.parse(Files.readString(file, StandardCharsets.UTF_8))
    } catch (e: Exception) {
        println("Файл повреждён или не PMV3: ${e.message}"); return null
    }
    val wrap = c.wraps.firstOrNull { it.type == KeyHierarchy.WRAP_TYPE_MASTER }
    if (wrap == null) { println("Нет master-обёртки"); return null }
    val kek = Argon2Kdf.derive(master, c.kdf.toParams())
    val dek = KeyHierarchy.unwrapDek(wrap, kek)
    kek.fill(0)
    if (dek == null) { println("Неверный мастер-пароль"); return null }
    return c
}

private fun save(file: Path, c: VaultContainer) {
    val tmp = Files.createTempFile(file.parent, ".pmv3-", ".tmp")
    Files.writeString(tmp, ContainerCodec.serialize(c), StandardCharsets.UTF_8)
    try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING) // FS без atomic move
    }
}

private fun dekOf(c: VaultContainer, master: CharArray): ByteArray? {
    val wrap = c.wraps.first { it.type == KeyHierarchy.WRAP_TYPE_MASTER }
    val kek = Argon2Kdf.derive(master, c.kdf.toParams())
    val d = KeyHierarchy.unwrapDek(wrap, kek)
    kek.fill(0)
    return d
}

fun main(args: Array<String>) {
    if (args.isEmpty()) return usage()
    when (args[0]) {
        "gen" -> {
            val n = args.getOrNull(1)?.toIntOrNull() ?: 16
            val o = PasswordGenerator.Options(length = n)
            println(PasswordGenerator.generate(o))
            println("~${PasswordGenerator.entropyBits(o).toInt()} бит")
        }
        "create" -> {
            val file = Path.of(args[1])
            val m1 = master("Мастер-пароль: ")
            val m2 = master("Повторите: ")
            if (!m1.contentEquals(m2)) { println("Не совпадают"); return }
            val salt = Argon2Kdf.newSalt()
            val params = Argon2Kdf.Params(salt = salt)
            val kek = Argon2Kdf.derive(m1, params)
            val dek = KeyHierarchy.newDek()
            val wrap = KeyHierarchy.wrapDek(dek, kek)
            kek.fill(0); m1.fill('\u0000'); m2.fill('\u0000')
            save(file, VaultContainer(KdfConfig(params.memoryKiB, params.iterations, params.parallelism, salt), listOf(wrap), emptyList()))
            println("Создан: $file")
        }
        "list", "add", "show", "del", "merge" -> {
            val file = Path.of(args[1])
            val m = master("Мастер-пароль: ")
            val c = try {
                ContainerCodec.parse(Files.readString(file, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                println("Файл повреждён или не PMV3"); m.fill('\u0000'); return
            }
            val dek = dekOf(c, m)
            m.fill('\u0000')
            if (dek == null) { println("Неверный мастер-пароль"); return }
            when (args[0]) {
                "list" -> c.records.mapNotNull { e ->
                    ContainerCodec.openPayload(dek, e)?.takeIf { !it.deleted }?.let { e.id to it }
                }.sortedBy { it.second.description.lowercase() }
                    .forEach { (id, p) -> println("$id  ${p.description}  ${TimeFormat.human(p.updatedAt)}") }
                "add" -> {
                    val desc = args[2]
                    val pass = when (val a = args.getOrNull(3)) {
                        null -> String(master("Пароль: "))
                        "gen" -> PasswordGenerator.generate()
                        else -> a
                    }
                    val now = System.currentTimeMillis()
                    val id = UUID.randomUUID().toString()
                    val env = ContainerCodec.sealPayload(dek, id, RecordPayload(desc, pass, now, now))
                    save(file, c.copy(records = c.records + env))
                    println("Добавлено: $id")
                }
                "show" -> {
                    val e = c.records.firstOrNull { it.id == args[2] }
                    val p = e?.let { ContainerCodec.openPayload(dek, it) }
                    if (p == null) println("Не найдено") else println("${p.description}\n${p.password}\n${TimeFormat.human(p.updatedAt)}")
                }
                "del" -> {
                    val e = c.records.firstOrNull { it.id == args[2] }
                    val p = e?.let { ContainerCodec.openPayload(dek, it) }
                    if (p == null) { println("Не найдено"); return }
                    val now = System.currentTimeMillis()
                    val env = ContainerCodec.sealPayload(dek, e.id, p.copy(deleted = true, updatedAt = now))
                    save(file, c.copy(records = c.records.map { if (it.id == e.id) env else it }))
                    println("Удалено (tombstone): ${e.id}")
                }
                "merge" -> {
                    val other = try {
                        ContainerCodec.parse(Files.readString(Path.of(args[2]), StandardCharsets.UTF_8))
                    } catch (e: Exception) {
                        println("Копия повреждена или не PMV3"); return
                    }
                    save(file, VaultMerger.merge(dek, c, other))
                    println("Слито: ${args[2]} -> $file")
                }
            }
        }
        else -> usage()
    }
}