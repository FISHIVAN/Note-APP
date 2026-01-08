package com.example.note.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

object RichTextHelper {

    // Spans
    val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)
    val ItalicSpan = SpanStyle(fontStyle = FontStyle.Italic)
    val HeadingSpan = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    
    // We can't define generic ColorSpan/BgSpan because they depend on the color.
    
    enum class RichTextStyle {
        Bold, Italic, Heading, List
        // Color and Bg are handled separately because they carry data
    }

    private fun Color.toHex(): String {
        val alpha = (this.alpha * 255).toInt()
        val red = (this.red * 255).toInt()
        val green = (this.green * 255).toInt()
        val blue = (this.blue * 255).toInt()
        return String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
    }

    private fun parseColor(hex: String): Color {
        return try {
            val colorInt = android.graphics.Color.parseColor(hex)
            Color(colorInt)
        } catch (e: Exception) {
            Color.Black
        }
    }

    /**
     * Converts Markdown (extended with [color] and [bg] tags) to AnnotatedString.
     */
    fun markdownToRichText(markdown: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val lines = markdown.split("\n")
        
        for (i in lines.indices) {
            val line = lines[i]
            var processedLine = line
            var isHeading = false
            var isList = false
            
            // Check Line Styles
            if (line.startsWith("# ")) {
                isHeading = true
                processedLine = line.substring(2)
            } else if (line.startsWith("- ")) {
                isList = true
                processedLine = line.substring(2)
                processedLine = "• $processedLine"
            }
            
            val lineBuilder = AnnotatedString.Builder()
            appendLineWithStyles(lineBuilder, processedLine)
            
            if (isHeading) {
                lineBuilder.addStyle(HeadingSpan, 0, lineBuilder.length)
            }
            
            builder.append(lineBuilder.toAnnotatedString())
            if (i < lines.size - 1) {
                builder.append("\n")
            }
        }
        
        return builder.toAnnotatedString()
    }

    private fun appendLineWithStyles(builder: AnnotatedString.Builder, text: String) {
        // We use a simplified state machine / stack approach.
        // Supported tokens: **, _, [color:HEX], [/color], [bg:HEX], [/bg]
        
        // Find all tokens
        val tokens = mutableListOf<Token>()
        
        // Regex for tokens
        // **
        val boldMatcher = Pattern.compile("\\*\\*").matcher(text)
        while (boldMatcher.find()) tokens.add(Token(boldMatcher.start(), "**", TokenType.Bold))
        
        // _
        // Ignore intraword underscores (GFM style) to avoid bad parsing of snake_case (e.g. FISH_200516)
        val italicMatcher = Pattern.compile("(?<![\\p{L}\\p{N}])_|_(?![\\p{L}\\p{N}])").matcher(text)
        while (italicMatcher.find()) tokens.add(Token(italicMatcher.start(), "_", TokenType.Italic))
        
        // Color
        val colorStartMatcher = Pattern.compile("\\[color:(#[0-9A-Fa-f]{8})\\]").matcher(text)
        while (colorStartMatcher.find()) tokens.add(Token(colorStartMatcher.start(), colorStartMatcher.group(), TokenType.ColorStart, colorStartMatcher.group(1)))
        
        val colorEndMatcher = Pattern.compile("\\[/color\\]").matcher(text)
        while (colorEndMatcher.find()) tokens.add(Token(colorEndMatcher.start(), "[/color]", TokenType.ColorEnd))
        
        // Bg
        val bgStartMatcher = Pattern.compile("\\[bg:(#[0-9A-Fa-f]{8})\\]").matcher(text)
        while (bgStartMatcher.find()) tokens.add(Token(bgStartMatcher.start(), bgStartMatcher.group(), TokenType.BgStart, bgStartMatcher.group(1)))
        
        val bgEndMatcher = Pattern.compile("\\[/bg\\]").matcher(text)
        while (bgEndMatcher.find()) tokens.add(Token(bgEndMatcher.start(), "[/bg]", TokenType.BgEnd))
        
        tokens.sortBy { it.index }
        
        var currentIndex = 0
        val styleStack = ArrayDeque<ActiveStyle>()
        
        // To build the AnnotatedString without the tags, we need to track "output index".
        // But tags are removed.
        // We can just iterate tokens.
        
        for (token in tokens) {
            // Append text before token
            if (token.index > currentIndex) {
                val segment = text.substring(currentIndex, token.index)
                appendSegment(builder, segment, styleStack)
            }
            
            // Process Token
            when (token.type) {
                TokenType.Bold -> {
                    toggleStyleInStack(styleStack, ActiveStyle.Bold)
                }
                TokenType.Italic -> {
                    toggleStyleInStack(styleStack, ActiveStyle.Italic)
                }
                TokenType.ColorStart -> {
                    styleStack.addLast(ActiveStyle.Color(parseColor(token.data!!)))
                }
                TokenType.ColorEnd -> {
                    // Remove last Color style (from back)
                    removeLastStyleOfType(styleStack, ActiveStyle.Color::class.java)
                }
                TokenType.BgStart -> {
                    styleStack.addLast(ActiveStyle.Bg(parseColor(token.data!!)))
                }
                TokenType.BgEnd -> {
                    removeLastStyleOfType(styleStack, ActiveStyle.Bg::class.java)
                }
            }
            
            currentIndex = token.index + token.raw.length
        }
        
        // Append remaining text
        if (currentIndex < text.length) {
            appendSegment(builder, text.substring(currentIndex), styleStack)
        }
    }
    
    private fun appendSegment(builder: AnnotatedString.Builder, text: String, stack:  ArrayDeque<ActiveStyle>) {
        val start = builder.length
        builder.append(text)
        val end = builder.length
        
        for (style in stack) {
            when (style) {
                ActiveStyle.Bold -> builder.addStyle(BoldSpan, start, end)
                ActiveStyle.Italic -> builder.addStyle(ItalicSpan, start, end)
                is ActiveStyle.Color -> builder.addStyle(SpanStyle(color = style.color), start, end)
                is ActiveStyle.Bg -> builder.addStyle(SpanStyle(background = style.color), start, end)
            }
        }
    }
    
    private fun toggleStyleInStack(stack: ArrayDeque<ActiveStyle>, style: ActiveStyle) {
        if (stack.contains(style)) {
            stack.remove(style)
        } else {
            stack.addLast(style)
        }
    }
    
    private fun <T> removeLastStyleOfType(stack: ArrayDeque<ActiveStyle>, type: Class<T>) {
        for (i in stack.indices.reversed()) {
            val s = stack[i]
            if (type.isInstance(s)) {
                stack.removeAt(i)
                return
            }
        }
    }

    private data class Token(val index: Int, val raw: String, val type: TokenType, val data: String? = null)
    private enum class TokenType { Bold, Italic, ColorStart, ColorEnd, BgStart, BgEnd }
    
    private sealed class ActiveStyle {
        object Bold : ActiveStyle()
        object Italic : ActiveStyle()
        data class Color(val color: androidx.compose.ui.graphics.Color) : ActiveStyle()
        data class Bg(val color: androidx.compose.ui.graphics.Color) : ActiveStyle()
    }

    /**
     * Converts AnnotatedString back to Markdown.
     */
    fun richTextToMarkdown(text: AnnotatedString): String {
        val sb = StringBuilder()
        val lines = text.text.split("\n")
        var offset = 0
        
        for (i in lines.indices) {
            val lineContent = lines[i]
            val lineEnd = offset + lineContent.length
            
            val lineSpans = text.spanStyles.filter { 
                it.start < lineEnd && it.end > offset 
            }
            
            var isHeading = false
            if (lineSpans.any { it.item.fontSize == 24.sp }) {
                isHeading = true
            }
            
            var processedLine = lineContent
            var isList = false
            if (processedLine.startsWith("• ")) {
                isList = true
                processedLine = processedLine.substring(2)
            }
            
            if (isHeading) sb.append("# ")
            if (isList) sb.append("- ")
            
            val contentStartInLine = if (isList) 2 else 0
            val lineBase = offset
            val reconstruction = reconstructLine(
                text, 
                lineBase + contentStartInLine, 
                lineBase + lineContent.length
            )
            sb.append(reconstruction)
            
            if (i < lines.size - 1) sb.append("\n")
            offset += lineContent.length + 1
        }
        
        return sb.toString()
    }

    private fun reconstructLine(text: AnnotatedString, start: Int, end: Int): String {
        val sb = StringBuilder()
        // We track the active stack of styles and emit tags when they change.
        // Styles to track: Bold, Italic, Color, Bg.
        
        // Since AnnotatedString allows arbitrary overlapping, but our Markdown (and especially HTML-like tags)
        // prefers strict nesting or stack-like behavior.
        // However, we can just open/close tags as needed.
        // Simplest strategy: At each character, check what styles are active.
        // Compare with previous character's active styles.
        // Close styles that are no longer active.
        // Open styles that became active.
        // Order of closing/opening matters for nesting.
        // We try to maintain a consistent order: Bg -> Color -> Bold -> Italic (Inner).
        
        var activeBg: Color? = null
        var activeColor: Color? = null
        var activeBold = false
        var activeItalic = false
        
        for (i in start until end) {
            // Determine active styles at index i
            val spans = text.spanStyles.filter { it.start <= i && i < it.end }
            
            var newBg: Color? = null
            var newColor: Color? = null
            var newBold = false
            var newItalic = false
            
            for (span in spans) {
                if (span.item.fontWeight == FontWeight.Bold) newBold = true
                if (span.item.fontStyle == FontStyle.Italic) newItalic = true
                if (span.item.color != Color.Unspecified) newColor = span.item.color
                if (span.item.background != Color.Unspecified) newBg = span.item.background
            }
            
            // Calculate Diff
            // We need to transition from (activeBg, activeColor, activeBold, activeItalic)
            // to (newBg, newColor, newBold, newItalic).
            // Closing order: Italic -> Bold -> Color -> Bg.
            // Opening order: Bg -> Color -> Bold -> Italic.
            
            // Close if changed
            if (activeItalic && !newItalic) { sb.append("_"); activeItalic = false }
            if (activeBold && !newBold) { sb.append("**"); activeBold = false }
            if (activeColor != null && activeColor != newColor) { sb.append("[/color]"); activeColor = null }
            if (activeBg != null && activeBg != newBg) { sb.append("[/bg]"); activeBg = null }
            
            // Re-open/Open if needed
            // If we closed an outer tag (e.g. Bg), we implicitly closed inner ones (Color, Bold...).
            // Wait, our "Close" logic above assumes we can close them independently.
            // But `[bg][color]...[/bg]` is invalid if we don't close color first.
            // So if Bg changes, we MUST close Color, Bold, Italic too, then re-open them.
            
            // Correct Logic:
            // Check changes from Outer to Inner.
            // If Outer changes, all Inner must close and re-evaluate.
            
            // Layers: 1. Bg, 2. Color, 3. Bold, 4. Italic
            var dirty = false
            
            // 1. Bg
            if (activeBg != newBg) {
                if (activeItalic) { sb.append("_"); activeItalic = false }
                if (activeBold) { sb.append("**"); activeBold = false }
                if (activeColor != null) { sb.append("[/color]"); activeColor = null }
                if (activeBg != null) { sb.append("[/bg]"); activeBg = null }
                
                if (newBg != null) { sb.append("[bg:${newBg.toHex()}]"); activeBg = newBg }
                dirty = true
            }
            
            // 2. Color
            if (dirty || activeColor != newColor) {
                if (activeItalic) { sb.append("_"); activeItalic = false }
                if (activeBold) { sb.append("**"); activeBold = false }
                if (activeColor != null) { sb.append("[/color]"); activeColor = null }
                
                if (newColor != null) { sb.append("[color:${newColor.toHex()}]"); activeColor = newColor }
                dirty = true
            }
            
            // 3. Bold
            if (dirty || activeBold != newBold) {
                if (activeItalic) { sb.append("_"); activeItalic = false }
                if (activeBold) { sb.append("**"); activeBold = false }
                
                if (newBold) { sb.append("**"); activeBold = true }
                dirty = true
            }
            
            // 4. Italic
            if (dirty || activeItalic != newItalic) {
                if (activeItalic) { sb.append("_"); activeItalic = false }
                if (newItalic) { sb.append("_"); activeItalic = true }
            }
            
            sb.append(text.text[i])
        }
        
        // Close all at end of line
        if (activeItalic) sb.append("_")
        if (activeBold) sb.append("**")
        if (activeColor != null) sb.append("[/color]")
        if (activeBg != null) sb.append("[/bg]")
        
        return sb.toString()
    }
    
    // ... existing toggleStyle methods adapted ...
    // Since we added applyStyle, we can generalize.
    
    fun toggleStyle(value: TextFieldValue, style: RichTextStyle): TextFieldValue {
        // Reuse existing logic for Bold/Italic/Heading/List
        // Map RichTextStyle to Span or logic
        return when (style) {
            RichTextStyle.Bold -> toggleSpanStyle(value, BoldSpan, value.selection.start, value.selection.end)
            RichTextStyle.Italic -> toggleSpanStyle(value, ItalicSpan, value.selection.start, value.selection.end)
            RichTextStyle.Heading -> toggleLineStyle(value, HeadingSpan)
            RichTextStyle.List -> toggleListStyle(value)
        }
    }
    
    fun setColor(value: TextFieldValue, color: Color): TextFieldValue {
        if (value.selection.collapsed) return value
        val start = value.selection.start
        val end = value.selection.end
        
        // Remove existing color spans in range
        var newValue = removeColorSpans(value, start, end)
        
        // Add new if valid
        if (color != Color.Unspecified) {
            val builder = AnnotatedString.Builder(newValue.annotatedString)
            builder.addStyle(SpanStyle(color = color), start, end)
            newValue = newValue.copy(annotatedString = builder.toAnnotatedString())
        }
        return newValue
    }

    fun setHighlight(value: TextFieldValue, color: Color): TextFieldValue {
        if (value.selection.collapsed) return value
        val start = value.selection.start
        val end = value.selection.end
        
        var newValue = removeHighlightSpans(value, start, end)
        
        if (color != Color.Unspecified) {
            val builder = AnnotatedString.Builder(newValue.annotatedString)
            builder.addStyle(SpanStyle(background = color), start, end)
            newValue = newValue.copy(annotatedString = builder.toAnnotatedString())
        }
        return newValue
    }

    private fun removeColorSpans(value: TextFieldValue, start: Int, end: Int): TextFieldValue {
        val builder = AnnotatedString.Builder(value.text)
        for (s in value.annotatedString.spanStyles) {
            // Only target spans that strictly define a color (and assume they are primarily color spans)
            // To be safe against composite spans, we should ideally rebuild the span without color.
            // But assuming our helper adds distinct spans:
            val isColorSpan = s.item.color != Color.Unspecified
            
            if (isColorSpan && s.start < end && s.end > start) {
                // Intersecting
                // If it's a composite span (e.g. Bold + Color), this removes Bold too. 
                // We'll assume distinct spans for now based on applyStyle usage.
                if (s.start < start) builder.addStyle(s.item, s.start, start)
                if (s.end > end) builder.addStyle(s.item, end, s.end)
            } else {
                builder.addStyle(s.item, s.start, s.end)
            }
        }
        return value.copy(annotatedString = builder.toAnnotatedString())
    }

    private fun removeHighlightSpans(value: TextFieldValue, start: Int, end: Int): TextFieldValue {
        val builder = AnnotatedString.Builder(value.text)
        for (s in value.annotatedString.spanStyles) {
            val isBgSpan = s.item.background != Color.Unspecified
            if (isBgSpan && s.start < end && s.end > start) {
                if (s.start < start) builder.addStyle(s.item, s.start, start)
                if (s.end > end) builder.addStyle(s.item, end, s.end)
            } else {
                builder.addStyle(s.item, s.start, s.end)
            }
        }
        return value.copy(annotatedString = builder.toAnnotatedString())
    }

    fun applyStyle(value: TextFieldValue, span: SpanStyle): TextFieldValue {
         // Applies a specific span (Color or Bg) to selection.
         // Unlike toggle, this usually just Overwrites/Adds.
         // If selection is collapsed, we might return value and rely on activeStyles (handled in UI).
         // But usually UI calls this when selection is present.
         if (value.selection.collapsed) return value
         
         // Remove existing conflicting styles?
         // E.g. if applying Red, remove Blue.
         // span.color vs span.background
         
         val builder = AnnotatedString.Builder(value.annotatedString)
         val start = value.selection.start
         val end = value.selection.end
         
         // We should remove any existing Color span in this range if we are applying Color.
         // But `AnnotatedString` supports multiple spans. Rendering handles it (last one wins usually?).
         // To keep it clean, we should probably remove conflicting spans.
         // But that's complex (splitting spans).
         // Let's just Add. Compose handles it.
         
         builder.addStyle(span, start, end)
         return value.copy(annotatedString = builder.toAnnotatedString())
    }

    private fun toggleLineStyle(value: TextFieldValue, style: SpanStyle): TextFieldValue {
        val text = value.text
        val selection = value.selection
        var lineStart = text.lastIndexOf('\n', selection.start - 1)
        if (lineStart == -1) lineStart = 0 else lineStart += 1
        
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        
        // Check if exists
        val exists = value.annotatedString.spanStyles.any { 
            it.item == style && it.start <= lineStart && it.end >= lineEnd
        }
        
        if (exists) {
            // Remove (Rebuild without it)
            // Simplified: return text without style span.
            return removeSpanStyle(value, style, lineStart, lineEnd)
        } else {
            val builder = AnnotatedString.Builder(value.annotatedString)
            builder.addStyle(style, lineStart, lineEnd)
            return value.copy(annotatedString = builder.toAnnotatedString())
        }
    }
    
    private fun toggleListStyle(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selection = value.selection
        var lineStart = text.lastIndexOf('\n', selection.start - 1)
        if (lineStart == -1) lineStart = 0 else lineStart += 1
        
        if (text.startsWith("• ", lineStart)) {
            return removeListBullet(value, lineStart)
        } else {
            return addListBullet(value, lineStart)
        }
    }

    private fun removeListBullet(value: TextFieldValue, index: Int): TextFieldValue {
        val text = value.text
        val newText = text.substring(0, index) + text.substring(index + 2)
        val builder = AnnotatedString.Builder(newText)
        val spans = value.annotatedString.spanStyles
        
        for (s in spans) {
            var start = s.start
            var end = s.end
            
            if (start >= index + 2) start -= 2
            else if (start > index) start = index
            
            if (end >= index + 2) end -= 2
            else if (end > index) end = index
            
            if (end > start) builder.addStyle(s.item, start, end)
        }
        val cur = value.selection.start
        val newCur = if (cur > index + 2) cur - 2 else if (cur > index) index else cur
        return value.copy(annotatedString = builder.toAnnotatedString(), selection = TextRange(newCur))
    }

    private fun addListBullet(value: TextFieldValue, index: Int): TextFieldValue {
        val text = value.text
        val newText = text.substring(0, index) + "• " + text.substring(index)
        val builder = AnnotatedString.Builder(newText)
        val spans = value.annotatedString.spanStyles
        
        for (s in spans) {
            var start = s.start
            var end = s.end
            
            if (start >= index) start += 2
            if (end >= index) end += 2
            
            builder.addStyle(s.item, start, end)
        }
        val cur = value.selection.start
        val newCur = if (cur >= index) cur + 2 else cur
        return value.copy(annotatedString = builder.toAnnotatedString(), selection = TextRange(newCur))
    }

    private fun toggleSpanStyle(value: TextFieldValue, style: SpanStyle, start: Int, end: Int): TextFieldValue {
        val spans = value.annotatedString.spanStyles
        // Check if fully covered
        val covering = spans.filter { it.item == style && it.start <= start && it.end >= end }
        
        if (covering.isNotEmpty()) {
            // Remove
            return removeSpanStyle(value, style, start, end)
        } else {
            // Add
            val builder = AnnotatedString.Builder(value.annotatedString)
            builder.addStyle(style, start, end)
            return value.copy(annotatedString = builder.toAnnotatedString())
        }
    }
    
    private fun removeSpanStyle(value: TextFieldValue, style: SpanStyle, start: Int, end: Int): TextFieldValue {
        val builder = AnnotatedString.Builder(value.text)
        for (s in value.annotatedString.spanStyles) {
            if (s.item == style) {
                // Intersect?
                if (s.start < end && s.end > start) {
                    // Split or Shrink
                    if (s.start < start) builder.addStyle(s.item, s.start, start)
                    if (s.end > end) builder.addStyle(s.item, end, s.end)
                } else {
                    builder.addStyle(s.item, s.start, s.end)
                }
            } else {
                builder.addStyle(s.item, s.start, s.end)
            }
        }
        return value.copy(annotatedString = builder.toAnnotatedString())
    }

    fun applyDiff(
        oldVal: TextFieldValue, 
        newVal: TextFieldValue, 
        activeStyles: Set<RichTextStyle>,
        activeColor: Color?,
        activeBg: Color?
    ): TextFieldValue {
        val oldText = oldVal.text
        val newText = newVal.text
        val oldSpans = oldVal.annotatedString.spanStyles
        
        var prefixEnd = 0
        val minLen = minOf(oldText.length, newText.length)
        while (prefixEnd < minLen && oldText[prefixEnd] == newText[prefixEnd]) {
            prefixEnd++
        }
        
        var oldSuffixStart = oldText.length
        var newSuffixStart = newText.length
        
        while (oldSuffixStart > prefixEnd && newSuffixStart > prefixEnd && 
               oldText[oldSuffixStart - 1] == newText[newSuffixStart - 1]) {
            oldSuffixStart--
            newSuffixStart--
        }
        
        val deletedLen = oldSuffixStart - prefixEnd
        val insertedLen = newSuffixStart - prefixEnd
        
        val builder = AnnotatedString.Builder(newText)
        
        for (span in oldSpans) {
            val start = span.start
            val end = span.end
            
            var newStart = start
            if (start >= oldSuffixStart) {
                newStart = start - deletedLen + insertedLen
            } else if (start >= prefixEnd) {
                newStart = prefixEnd
            }
            
            var newEnd = end
            if (end >= oldSuffixStart) {
                newEnd = end - deletedLen + insertedLen
            } else if (end >= prefixEnd) {
                newEnd = prefixEnd
            }
            
            if (newEnd > newStart) {
                builder.addStyle(span.item, newStart, newEnd)
            }
        }
        
        if (insertedLen > 0) {
            val insertStart = prefixEnd
            val insertEnd = prefixEnd + insertedLen
            
            if (activeStyles.contains(RichTextStyle.Bold)) builder.addStyle(BoldSpan, insertStart, insertEnd)
            if (activeStyles.contains(RichTextStyle.Italic)) builder.addStyle(ItalicSpan, insertStart, insertEnd)
            if (activeColor != null) builder.addStyle(SpanStyle(color = activeColor), insertStart, insertEnd)
            if (activeBg != null) builder.addStyle(SpanStyle(background = activeBg), insertStart, insertEnd)
        }
        
        return newVal.copy(annotatedString = builder.toAnnotatedString())
    }
    
    fun getActiveStyles(value: TextFieldValue): Triple<Set<RichTextStyle>, Color?, Color?> {
        val styles = mutableSetOf<RichTextStyle>()
        val selection = value.selection
        val spans = value.annotatedString.spanStyles
        val cursor = selection.start
        // Use exclusive check for 0-width cursor usually, but inclusive if inside.
        // We look for spans that CONTAIN the cursor.
        // If cursor is at end of span, it depends on direction, but usually previous style continues.
        // Let's use `cursor > start && cursor <= end` (Standard).
        // Exception: Start of text? `start <= cursor && end >= cursor`?
        
        // Simple heuristic: Take styles at cursor-1.
        val checkIndex = if (cursor > 0) cursor - 1 else 0
        
        // Check spans covering checkIndex
        val covering = spans.filter { it.start <= checkIndex && it.end > checkIndex }
        
        var color: Color? = null
        var bg: Color? = null
        
        for (s in covering) {
            if (s.item.fontWeight == FontWeight.Bold) styles.add(RichTextStyle.Bold)
            if (s.item.fontStyle == FontStyle.Italic) styles.add(RichTextStyle.Italic)
            if (s.item.fontSize == 24.sp) styles.add(RichTextStyle.Heading) // Rough check
            
            if (s.item.color != Color.Unspecified) color = s.item.color
            if (s.item.background != Color.Unspecified) bg = s.item.background
        }
        
        // Line Styles
        val text = value.text
        var lineStart = text.lastIndexOf('\n', cursor - 1)
        if (lineStart == -1) lineStart = 0 else lineStart += 1
        if (text.startsWith("• ", lineStart)) styles.add(RichTextStyle.List)
        // Heading is also a line style, double check
        if (covering.any { it.item == HeadingSpan }) styles.add(RichTextStyle.Heading)

        return Triple(styles, color, bg)
    }
}
