package com.huijui.sunext.edition.core.root

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 启动镜像重组。
 *
 * ── 为什么需要它 ──
 *
 * 这个分区（init_boot）的真实布局是：
 *
 * ```
 * [0]            ANDROID! 头（v4, header_size=1584）
 * [4096]         ramdisk（压缩后 ~3.29MB）
 * [3,297,280]    VBmeta 结构（AVB0, 832 字节, algorithm=NONE 未签名）
 * [...]          空白
 * [8,388,544]    AVB 页脚（AVBf），记录 vbmeta_offset / original_image_size
 * ```
 *
 * 而 `hsud_cli repack` 只产出「头 + kernel + ramdisk」这一段的镜像，
 * 注入模块后体积会涨到 3.6MB **以上** —— 直接 dd 回去就会把 3,297,280 处的
 * vbmeta 结构整个覆盖掉，而分区末尾的 AVB 页脚仍然指向那个位置。
 *
 * 结果就是分区结构自相矛盾：下次再读出来解包，ramdisk 全是垃圾，
 * 所有解压器都失败，表现为「ramdisk 里没有 init」。真机上已经踩过这个坑。
 *
 * ── 这里做的事 ──
 *
 * 写盘前重新拼一个完整的 8MB 镜像：
 *   1. 前段放 repack 出来的新镜像
 *   2. 按 4096 对齐后，把原来的 vbmeta 原样搬过去
 *   3. 末尾重写 AVB 页脚，把 vbmeta_offset / original_image_size 更新成新值
 *
 * 由于该 vbmeta 的 algorithm = NONE、auth_block = 0、aux_block = 0，
 * 里面没有任何哈希或签名，所以搬位置不需要重新签名，结构自洽即可。
 */
object BootImageAssembler {

    data class Result(val ok: Boolean, val message: String)

    private const val PAGE = 4096
    private const val FOOTER_SIZE = 64
    private const val AVB_MAGIC = "AVBf"
    private const val AVB0_MAGIC = "AVB0"

    /** 把 repack 产物与原始 vbmeta / 页脚拼成一份完整分区镜像。 */
    fun assemble(stock: File, patched: File, out: File): Result {
        if (!stock.isFile || !patched.isFile) {
            return Result(false, "输入文件缺失：stock=${stock.exists()} patched=${patched.exists()}")
        }

        val stockBytes = stock.readBytes()
        val patchedBytes = patched.readBytes()
        if (stockBytes.size < PAGE * 2) return Result(false, "原厂镜像是空的")
        if (patchedBytes.size < PAGE) return Result(false, "修补后的镜像异常（太小）")

        // ── 读 AVB 页脚 ──
        val footerOffset = stockBytes.size - FOOTER_SIZE
        val footerMagic = String(stockBytes, footerOffset, 4, Charsets.US_ASCII)
        if (footerMagic != AVB_MAGIC) {
            // 没有 AVB 页脚：说明这个分区不靠 AVB 收尾，直接按原样写 patched 即可
            out.writeBytes(patchedBytes)
            return Result(true, "该镜像没有 AVB 页脚，按原样写入")
        }

        val fb = ByteBuffer.wrap(stockBytes, footerOffset, FOOTER_SIZE).order(ByteOrder.BIG_ENDIAN)
        fb.position(footerOffset + 12)
        val origSize = fb.long
        val vbOffset = fb.long
        val vbSize = fb.long

        if (vbOffset <= 0 || vbSize <= 0 || vbOffset + vbSize > stockBytes.size) {
            return Result(false, "AVB 页脚里的 vbmeta 偏移不合理（off=$vbOffset size=$vbSize）")
        }

        val vbmeta = stockBytes.copyOfRange(vbOffset.toInt(), (vbOffset + vbSize).toInt())
        if (String(vbmeta, 0, 4, Charsets.US_ASCII) != AVB0_MAGIC) {
            return Result(false, "页脚指向的位置不是有效的 vbmeta（缺少 AVB0 魔数）")
        }

        // ── 新布局 ──
        val newVbOffset = alignUp(patchedBytes.size, PAGE)
        val footerAt = stockBytes.size - FOOTER_SIZE
        if (newVbOffset + vbSize > footerAt) {
            return Result(
                false,
                "修补后镜像过大（${patchedBytes.size / 1024 / 1024}MB），vbmeta 放不下，已中止"
            )
        }

        val finalBuf = ByteArray(stockBytes.size)   // 其余区域清零，保持干净
        System.arraycopy(patchedBytes, 0, finalBuf, 0, patchedBytes.size)
        System.arraycopy(vbmeta, 0, finalBuf, newVbOffset, vbmeta.size)

        // 重写页脚：original_image_size = vbmeta 起点，vbmeta_offset = 新位置
        val nf = ByteBuffer.wrap(finalBuf, footerAt, FOOTER_SIZE).order(ByteOrder.BIG_ENDIAN)
        nf.put(footerOffset - footerAt, AVB_MAGIC.toByteArray(Charsets.US_ASCII)) // magic
        nf.position(footerAt + 4); nf.putInt(1); nf.putInt(0)                      // version 1.0
        nf.putLong(newVbOffset.toLong())                                           // original_image_size
        nf.putLong(newVbOffset.toLong())                                           // vbmeta_offset
        nf.putLong(vbSize)                                                         // vbmeta_size

        out.writeBytes(finalBuf)

        return Result(
            true,
            "已重组：镜像 ${patchedBytes.size / 1024}KB，vbmeta 搬到 $newVbOffset（原 $vbOffset），" +
                "整块 ${finalBuf.size / 1024 / 1024}MB"
        )
    }

    private fun alignUp(v: Int, a: Int): Int = (v + a - 1) / a * a
}
