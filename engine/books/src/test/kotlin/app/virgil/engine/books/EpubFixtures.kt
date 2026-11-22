package app.virgil.engine.books

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Gera EPUBs mínimos, porém realistas, para os testes. */
object EpubFixtures {
    fun write(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            // "mimetype" primeiro e sem compressão, como manda a especificação.
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime); zip.closeEntry()
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }

    const val CONTAINER = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    fun xhtml(body: String) = """<?xml version="1.0" encoding="utf-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>x</title><style>p{text-indent:1em}</style></head><body>$body</body></html>"""

    val PNG: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)

    /** EPUB 3 com NAV, capa por propriedade, página de capa, notas, verso e imagem no texto. */
    fun epub3(file: File, declaredLanguage: String = "en") = write(file, mapOf(
        "META-INF/container.xml" to CONTAINER.toByteArray(),
        "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Mrs Dalloway</dc:title><dc:creator>Virginia Woolf</dc:creator>
                <dc:language>$declaredLanguage</dc:language><dc:publisher>Hogarth Press</dc:publisher><dc:date>1925</dc:date>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="cover-img" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                <item id="fig" href="images/fig%201.png" media-type="image/png"/>
                <item id="coverpage" href="text/cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                <item id="notes" href="text/notes.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="coverpage"/><itemref idref="c1"/><itemref idref="notes" linear="no"/><itemref idref="c2"/></spine>
            </package>""".trimIndent().toByteArray(),
        "OEBPS/nav.xhtml" to xhtml("""<nav epub:type="toc"><ol>
              <li><a href="text/ch1.xhtml">One</a><ol><li><a href="text/ch1.xhtml#scene2">Scene two</a></li></ol></li>
              <li><a href="text/ch2.xhtml">Two</a></li></ol></nav>""").toByteArray(),
        "OEBPS/images/cover.png" to PNG,
        "OEBPS/images/fig 1.png" to PNG,
        "OEBPS/text/cover.xhtml" to xhtml("""<div><img src="../images/cover.png" alt="Capa"/></div>""").toByteArray(),
        "OEBPS/text/ch1.xhtml" to xhtml("""
              <h1>One</h1>
              <p>Mrs. Dalloway said she would buy the <em>flowers</em> herself.<a epub:type="noteref" href="notes.xhtml#n1"><sup>1</sup></a></p>
              <p>For Lucy had her work cut
                 out for her.</p>
              <hr/>
              <p id="scene2">What a lark! What a plunge!</p>
              <div class="poem"><p>The sea is calm to-night.<br/>The tide is full, the moon lies fair</p></div>
              <blockquote><p>Fear no more the heat o’ the sun</p></blockquote>
              <p><img src="../images/fig%201.png"/></p>
              <script>alert(1)</script>""").toByteArray(),
        "OEBPS/text/ch2.xhtml" to xhtml("""<section><h2>Two</h2><p>Big Ben strikes.</p><ul><li>First</li><li>Second</li></ul></section>""").toByteArray(),
        "OEBPS/text/notes.xhtml" to xhtml("""<aside id="n1" epub:type="footnote"><p>A note.</p></aside>""").toByteArray(),
    ))

    /** EPUB 2 com NCX, capa via meta name="cover", OPF na raiz e codificação declarada ISO-8859-1. */
    fun epub2(file: File) = write(file, mapOf(
        "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
        "book.opf" to """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:title>Madame Bovary</dc:title><dc:creator opf:role="aut">Gustave Flaubert</dc:creator>
                <dc:creator opf:role="trl">Someone Else</dc:creator><dc:language>fr</dc:language>
                <meta name="cover" content="cimg"/>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="cimg" href="c.jpg" media-type="image/jpeg"/>
                <item id="p1" href="p1.html" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="p1"/></spine>
            </package>""".trimIndent().toByteArray(),
        "toc.ncx" to """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
              <navPoint id="a"><navLabel><text>Première partie</text></navLabel><content src="p1.html"/>
                <navPoint id="b"><navLabel><text>Chapitre I</text></navLabel><content src="p1.html#c1"/></navPoint>
              </navPoint></navMap></ncx>""".toByteArray(),
        "c.jpg" to byteArrayOf(1, 2, 3),
        "p1.html" to ("""<?xml version="1.0" encoding="iso-8859-1"?><html><body><h2>Première partie</h2><p id="c1">Nous étions à l'Étude, quand le Proviseur entra.</p></body></html>""").toByteArray(Charsets.ISO_8859_1),
    ))

    fun drm(file: File) = write(file, mapOf(
        "META-INF/container.xml" to CONTAINER.toByteArray(),
        "META-INF/encryption.xml" to """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#"><enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/></enc:EncryptedData></encryption>""".toByteArray(),
        "OEBPS/content.opf" to "<package/>".toByteArray(),
    ))
}
