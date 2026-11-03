package com.davidadrianrg.atril.core.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTemplateTest {

    private fun args(t: String) = (CommandTemplate.parse(t) as CommandTemplate.Parsed.Ok).args

    @Test
    fun splitsOnWhitespaceAndKeepsQuotedGroups() {
        assertEquals(listOf("-L", "{core}", "{rom}"), args("-L {core} {rom}"))
        assertEquals(listOf("--name", "a b", "c d"), args("""--name "a b" 'c d'"""))
        assertEquals(listOf("x y"), args("""x\ y"""))
        assertEquals(emptyList<String>(), args("   "))
    }

    @Test
    fun unterminatedQuoteIsAnError() {
        assertTrue(CommandTemplate.parse("""-f "abc""") is CommandTemplate.Parsed.Error)
    }

    @Test
    fun romWithSpacesStaysASingleArgument() {
        val out = CommandTemplate.expand(args("-L {core} {rom}"), "/home/u/My Games/Zelda (USA).gba", "mgba_libretro.so")
        assertEquals(listOf("-L", "mgba_libretro.so", "/home/u/My Games/Zelda (USA).gba"), out)
    }

    @Test
    fun shellMetacharactersInThePathAreNotInterpreted() {
        val evil = "/roms/a; rm -rf ~ && \$(reboot) `id`.gba"
        val out = CommandTemplate.expand(args("{rom}"), evil, null)
        assertEquals(listOf(evil), out) // un único argumento literal
    }

    @Test
    fun placeholderInsideALargerArgumentIsReplaced() {
        val out = CommandTemplate.expand(args("-L /usr/lib/libretro/{core} {rom}"), "/r/g.gba", "mgba_libretro.so")
        assertEquals("/usr/lib/libretro/mgba_libretro.so", out[1])
    }

    @Test
    fun romIsAppendedWhenTheTemplateOmitsIt() {
        assertEquals(listOf("--fullscreen", "/r/g.z64"), CommandTemplate.expand(args("--fullscreen"), "/r/g.z64", null))
        assertEquals(listOf("/r/g.z64"), CommandTemplate.expand(emptyList(), "/r/g.z64", null))
    }

    @Test
    fun reportsUnknownPlaceholders() {
        assertEquals(listOf("{foo}"), CommandTemplate.unknownPlaceholders(args("{rom} {foo} {core}")))
        assertFalse(CommandTemplate.usesCore(args("{rom}")))
        assertTrue(CommandTemplate.usesCore(args("-L {core}")))
    }
}
