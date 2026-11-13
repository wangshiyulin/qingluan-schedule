package com.qingluan.schedule.transfer

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.*
import kotlinx.serialization.encodeToString

object ExchangeFiles {
    fun export(d: TimetableDocument, format: ExchangeFormat, week: Int): ByteArray =
        when (format) {
            ExchangeFormat.JSON -> BackupCodec.json.encodeToString(d.validate()).toByteArray()
            ExchangeFormat.CSV -> TabularCodec.csv(TabularCodec.rows(d)).toByteArray()
            ExchangeFormat.XLSX -> TabularCodec.xlsx(TabularCodec.rows(d))
            ExchangeFormat.ICS -> CalendarCodec.encode(d).toByteArray()
            ExchangeFormat.HTML -> TabularCodec.html(TabularCodec.rows(d)).toByteArray()
            ExchangeFormat.CODE -> ShareCode.encode(d).toByteArray()
            ExchangeFormat.QR -> {
                val code = ShareCode.encode(d)
                require(code.length <= 2600) { "课表内容超过二维码容量，请改用 JSON 或分享口令" }
                val bits =
                    MultiFormatWriter()
                        .encode(
                            code,
                            BarcodeFormat.QR_CODE,
                            1024,
                            1024,
                            mapOf(
                                EncodeHintType.CHARACTER_SET to "UTF-8",
                                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                                EncodeHintType.MARGIN to 3,
                            ),
                        )
                val image = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                val pixels =
                    IntArray(1024 * 1024) { i ->
                        if (bits[i % 1024, i / 1024]) Color.BLACK else Color.WHITE
                    }
                image.setPixels(pixels, 0, 1024, 0, 0, 1024, 1024)
                png(image)
            }
            ExchangeFormat.IMAGE -> png(render(d, week))
            ExchangeFormat.PDF -> {
                val image = render(d, week)
                val pdf = PdfDocument()
                try {
                    val page =
                        pdf.startPage(
                            PdfDocument.PageInfo.Builder(image.width, image.height, 1).create()
                        )
                    page.canvas.drawBitmap(image, 0f, 0f, null)
                    pdf.finishPage(page)
                    ByteArrayOutputStream().also { pdf.writeTo(it) }.toByteArray()
                } finally {
                    image.recycle()
                    pdf.close()
                }
            }
        }

    private fun png(bitmap: Bitmap): ByteArray =
        try {
            ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                .toByteArray()
        } finally {
            bitmap.recycle()
        }

    fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver
            .openInputStream(uri)
            ?.use { it.readNBytes(BackupCodec.MAX_BYTES + 1) }
            ?.also { require(it.size <= BackupCodec.MAX_BYTES) { "文件超过 8 MiB" } } ?: error("无法读取文件")

    fun decode(bytes: ByteArray): ImportResult {
        if (
            bytes.size > 8 &&
                ((bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) ||
                    (bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()))
        ) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val bitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: error("无法读取图片")
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                val result = runCatching {
                    MultiFormatReader()
                        .decode(
                            BinaryBitmap(HybridBinarizer(source)),
                            mapOf(
                                DecodeHintType.TRY_HARDER to true,
                                DecodeHintType.CHARACTER_SET to "UTF-8",
                            ),
                        )
                }
                    .getOrElse { error("图片中未找到可读取的课表二维码；普通课表截图请使用表格文件导入") }
                return ImportCodec.decode(result.text.toByteArray())
            } finally {
                bitmap.recycle()
            }
        }
        return ImportCodec.decode(bytes)
    }

    fun zipBackup(text: String): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("backup.json"))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
            .toByteArray()

    fun backup(bytes: ByteArray): Backup {
        if (bytes.take(2) == listOf(0x50.toByte(), 0x4b.toByte())) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var visited = 0
                var total = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    require(++visited <= 100) { "压缩包文件过多" }
                    val data = zip.readNBytes(BackupCodec.MAX_BYTES + 1)
                    total += data.size
                    require(total <= BackupCodec.MAX_BYTES) { "备份解压内容过大" }
                    if (e.name == "backup.json")
                        return BackupCodec.decode(data.toString(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            error("压缩包中没有 backup.json")
        }
        return BackupCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    private fun render(d: TimetableDocument, week: Int): Bitmap {
        require(week in 1..d.term.weeks)
        val width = 1400
        val row = 100f
        val top = 150f
        val left = 90f
        val dayWidth = (width - left - 20) / 7
        val image =
            Bitmap.createBitmap(
                width,
                (top + row * d.timetable.periods.size + 40).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(248, 251, 249))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(
            value: String,
            x: Float,
            y: Float,
            size: Float,
            color: Int = Color.rgb(24, 44, 34),
            bold: Boolean = false,
        ) {
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(value, x, y, paint)
        }
        text(d.term.name, 24f, 45f, 30f, bold = true)
        text("第 $week 周 · 青鸾课程表", 24f, 82f, 22f)
        val start =
            ScheduleEngine.weekStart(LocalDate.parse(d.term.startDate), d.term.firstDayOfWeek)
                .plusWeeks((week - 1).toLong())
        repeat(7) { day ->
            val date = start.plusDays(day.toLong())
            text(
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1] +
                    " ${date.monthValue}/${date.dayOfMonth}",
                left + day * dayWidth + 8,
                125f,
                23f,
                bold = true,
            )
        }
        d.timetable.periods.forEachIndexed { i, p ->
            val y = top + i * row
            text("${i+1}", 24f, y + 30, 24f, bold = true)
            text(clock(p.start), 10f, y + 58, 18f)
            text(clock(p.end), 10f, y + 83, 18f)
            paint.color = Color.rgb(220, 232, 226)
            canvas.drawLine(left, y, width - 20f, y, paint)
        }
        val prepared = ScheduleEngine.prepare(d.snapshot(), d.term.id)!!
        val colors = courseColorMap(d.courses)
        repeat(7) { day ->
            val list =
                lessonBlocks(prepared.onDate(start.plusDays(day.toLong()))).map { block ->
                    block.first.copy(
                        startPeriod = block.startPeriod,
                        endPeriod = block.endPeriod,
                        startMinute = block.startMinute,
                        endMinute = block.endMinute,
                        location = block.location,
                        teacher = block.teacher,
                    )
                }
            val lanes = mutableListOf<Int>()
            val assigned = list.map { o ->
                val n =
                    lanes.indexOfFirst { it < o.startPeriod }.let { if (it < 0) lanes.size else it }
                if (n == lanes.size) lanes += o.endPeriod else lanes[n] = o.endPeriod
                o to n
            }
            val count = maxOf(1, lanes.size)
            assigned.forEach { (o, lane) ->
                val x = left + day * dayWidth + lane * dayWidth / count + 3
                val y = top + (o.startPeriod - 1) * row + 3
                val w = dayWidth / count - 6
                val h = (o.endPeriod - o.startPeriod + 1) * row - 6
                val courseColor = (colors[o.course.id] ?: o.course.color).toInt()
                paint.color =
                    androidx.core.graphics.ColorUtils.blendARGB(courseColor, Color.WHITE, .86f)
                canvas.drawRoundRect(x, y, x + w, y + h, 16f, 16f, paint)
                val save = canvas.save()
                canvas.clipRect(x + 6, y + 4, x + w - 6, y + h - 4)
                var yy = y + 29
                val foreground = Color.rgb(32, 40, 48)
                listOf(o.course.name to true, o.teacher to false, o.location to false)
                    .filter { it.first.isNotBlank() }
                    .forEach { (value, bold) ->
                        paint.textSize = if (bold) 24f else 19f
                        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        var line = ""
                        value.forEach { c ->
                            if (paint.measureText(line + c) > w - 14 && line.isNotEmpty()) {
                                text(line, x + 7, yy, if (bold) 24f else 19f, foreground, bold)
                                yy += 28
                                line = ""
                            }
                            line += c
                        }
                        if (line.isNotBlank()) {
                            text(line, x + 7, yy, if (bold) 24f else 19f, foreground, bold)
                            yy += 28
                        }
                    }
                canvas.restoreToCount(save)
            }
        }
        return image
    }
}
