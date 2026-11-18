import Foundation
import UIKit

/// Wandelt KI-Antworten (Markdown) in Dateien um: Word (.docx), PDF und Excel (.xlsx, aus Markdown-Tabellen).
/// Ohne zusätzliche Bibliotheken – DOCX/XLSX sind ZIP-Dateien mit XML, PDF entsteht über den Druck-Renderer.
enum DocExport {
    enum Block {
        case heading(Int, String)
        case para(String)
        case item(String, String)
        case code(String)
        case table([[String]])
        case rule
    }

    // MARK: Markdown zerlegen

    static func parse(_ md: String) -> [Block] {
        var out: [Block] = []
        let lines = md.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")
        var para = ""
        func flush() {
            let t = para.trimmingCharacters(in: .whitespaces)
            if !t.isEmpty { out.append(.para(t)) }
            para = ""
        }
        var i = 0
        while i < lines.count {
            let t = lines[i].trimmingCharacters(in: .whitespaces)
            if t.hasPrefix("```") {
                flush()
                var code: [String] = []
                i += 1
                while i < lines.count, !lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("```") { code.append(lines[i]); i += 1 }
                out.append(.code(code.joined(separator: "\n")))
            } else if t.isEmpty {
                flush()
            } else if t.range(of: "^#{1,6}\\s", options: .regularExpression) != nil {
                flush()
                let level = t.prefix(while: { $0 == "#" }).count
                out.append(.heading(level, String(t.dropFirst(level)).trimmingCharacters(in: .whitespaces)))
            } else if t.range(of: "^(-{3,}|\\*{3,}|_{3,})$", options: .regularExpression) != nil {
                flush(); out.append(.rule)
            } else if t.hasPrefix("|"), i + 1 < lines.count,
                      lines[i + 1].trimmingCharacters(in: .whitespaces).range(of: "^\\|?\\s*:?-{2,}", options: .regularExpression) != nil {
                flush()
                var rows = [cells(t)]
                i += 2
                while i < lines.count, lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("|") {
                    rows.append(cells(lines[i].trimmingCharacters(in: .whitespaces))); i += 1
                }
                out.append(.table(rows))
                continue
            } else if let r = t.range(of: "^[-*+]\\s+", options: .regularExpression) {
                flush(); out.append(.item(String(t[r.upperBound...]), "•"))
            } else if let r = t.range(of: "^\\d+[.)]\\s+", options: .regularExpression) {
                flush(); out.append(.item(String(t[r.upperBound...]), String(t.prefix(while: { $0.isNumber })) + "."))
            } else {
                para += para.isEmpty ? t : " " + t
            }
            i += 1
        }
        flush()
        return out
    }

    private static func cells(_ row: String) -> [String] {
        var r = row.trimmingCharacters(in: .whitespaces)
        if r.hasPrefix("|") { r.removeFirst() }
        if r.hasSuffix("|") { r.removeLast() }
        return r.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }
    }

    struct Run { var text: String; var bold = false; var italic = false; var code = false }

    static func runs(_ text: String) -> [Run] {
        guard let re = try? NSRegularExpression(pattern: "\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*|\\[([^\\]]+)\\]\\(([^)]+)\\)") else { return [Run(text: text)] }
        let ns = text as NSString
        var res: [Run] = []
        var pos = 0
        for m in re.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            if m.range.location > pos { res.append(Run(text: ns.substring(with: NSRange(location: pos, length: m.range.location - pos)))) }
            func g(_ i: Int) -> String? { m.range(at: i).location == NSNotFound ? nil : ns.substring(with: m.range(at: i)) }
            if let b = g(1) ?? g(2) { res.append(Run(text: b, bold: true)) }
            else if let c = g(3) { res.append(Run(text: c, code: true)) }
            else if let it = g(4) { res.append(Run(text: it, italic: true)) }
            else { res.append(Run(text: "\(g(5) ?? "") (\(g(6) ?? ""))")) }
            pos = m.range.location + m.range.length
        }
        if pos < ns.length { res.append(Run(text: ns.substring(from: pos))) }
        return res
    }

    static func plain(_ text: String) -> String { runs(text).map(\.text).joined() }

    static func hasTable(_ md: String) -> Bool {
        parse(md).contains { if case .table = $0 { return true } else { return false } }
    }

    static func fileName(_ md: String, ext: String) -> String {
        let first = md.components(separatedBy: "\n").map { plain($0.trimmingCharacters(in: CharacterSet(charactersIn: "# ").union(.whitespaces))) }
            .first { !$0.isEmpty } ?? ""
        var base = String(first.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || $0 == " " || $0 == "-" || $0 == "_" })
            .trimmingCharacters(in: .whitespaces)
        base = String(base.prefix(40))
        if base.isEmpty { base = "KI-GenC" }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd_HHmm"
        return "\(base)_\(f.string(from: Date())).\(ext)"
    }

    private static func outURL(_ name: String) -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("export", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(name)
    }

    private static func esc(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
    }

    // MARK: Word

    private static func wRuns(_ text: String, bold: Bool = false, size: Int? = nil) -> String {
        runs(text).map { r in
            var p = ""
            if r.code { p += "<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\"/>" }
            if r.bold || bold { p += "<w:b/>" }
            if r.italic { p += "<w:i/>" }
            if let size { p += "<w:sz w:val=\"\(size)\"/>" }
            return "<w:r>\(p.isEmpty ? "" : "<w:rPr>\(p)</w:rPr>")<w:t xml:space=\"preserve\">\(esc(r.text))</w:t></w:r>"
        }.joined()
    }

    private static func wPara(_ inner: String, after: Int = 120, indent: Int = 0) -> String {
        "<w:p><w:pPr><w:spacing w:after=\"\(after)\"/>\(indent > 0 ? "<w:ind w:left=\"\(indent)\" w:hanging=\"280\"/>" : "")</w:pPr>\(inner)</w:p>"
    }

    static func docx(_ md: String) throws -> URL {
        var body = ""
        for b in parse(md) {
            switch b {
            case .heading(let l, let t): body += wPara(wRuns(t, bold: true, size: l == 1 ? 36 : l == 2 ? 30 : l == 3 ? 26 : 24), after: 160)
            case .para(let t): body += wPara(wRuns(t))
            case .item(let t, let m): body += wPara("<w:r><w:t xml:space=\"preserve\">\(esc(m))\t</w:t></w:r>" + wRuns(t), after: 60, indent: 560)
            case .code(let c):
                for line in c.components(separatedBy: "\n") {
                    body += wPara("<w:r><w:rPr><w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\"/><w:sz w:val=\"20\"/></w:rPr><w:t xml:space=\"preserve\">\(esc(line))</w:t></w:r>", after: 0)
                }
            case .rule: body += "<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"999999\"/></w:pBdr></w:pPr></w:p>"
            case .table(let rows):
                let bd = ["top", "left", "bottom", "right", "insideH", "insideV"].map { "<w:\($0) w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>" }.joined()
                let cols = max(rows.map(\.count).max() ?? 1, 1)
                body += "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>\(bd)</w:tblBorders></w:tblPr>"
                body += "<w:tblGrid>" + String(repeating: "<w:gridCol w:w=\"\(9638 / cols)\"/>", count: cols) + "</w:tblGrid>"
                for (ri, row) in rows.enumerated() {
                    body += "<w:tr>" + row.map { "<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>\(wPara(wRuns($0, bold: ri == 0), after: 40))</w:tc>" }.joined() + "</w:tr>"
                }
                body += "</w:tbl>" + wPara("", after: 60)
            }
        }
        let head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        let files: [(String, String)] = [
            ("[Content_Types].xml", head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"),
            ("_rels/.rels", head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"),
            ("word/document.xml", head + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>\(body)<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr></w:body></w:document>"),
        ]
        let url = outURL(fileName(md, ext: "docx"))
        try StoredZip.make(files).write(to: url, options: .atomic)
        return url
    }

    // MARK: Excel

    static func xlsx(_ md: String) throws -> URL {
        let tables: [[[String]]] = parse(md).compactMap { if case .table(let r) = $0 { return r } else { return nil } }
        guard !tables.isEmpty else { throw APIError(code: 0, message: "Die Antwort enthält keine Tabelle.") }
        func col(_ n: Int) -> String {
            var x = n + 1, s = ""
            while x > 0 { let r = (x - 1) % 26; s = String(UnicodeScalar(65 + r)!) + s; x = (x - 1) / 26 }
            return s
        }
        func number(_ v: String) -> Double? {
            var n = v.replacingOccurrences(of: "€", with: "").replacingOccurrences(of: "%", with: "").trimmingCharacters(in: .whitespaces)
            if n.range(of: "^-?[\\d.]*,\\d+$", options: .regularExpression) != nil || n.range(of: "^-?\\d{1,3}(\\.\\d{3})+$", options: .regularExpression) != nil {
                n = n.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")
            }
            return Double(n)
        }
        func sheet(_ rows: [[String]]) -> String {
            var x = ""
            for (ri, row) in rows.enumerated() {
                x += "<row r=\"\(ri + 1)\">"
                for (ci, raw) in row.enumerated() {
                    let v = plain(raw), ref = "\(col(ci))\(ri + 1)"
                    if ri > 0, let d = number(v) { x += "<c r=\"\(ref)\"><v>\(d)</v></c>" }
                    else { x += "<c r=\"\(ref)\" t=\"inlineStr\"\(ri == 0 ? " s=\"1\"" : "")><is><t xml:space=\"preserve\">\(esc(v))</t></is></c>" }
                }
                x += "</row>"
            }
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>\(x)</sheetData></worksheet>"
        }
        let head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        var files: [(String, String)] = []
        files.append(("[Content_Types].xml", head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + tables.indices.map { "<Override PartName=\"/xl/worksheets/sheet\($0 + 1).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }.joined() + "</Types>"))
        files.append(("_rels/.rels", head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"))
        files.append(("xl/workbook.xml", head + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
            + tables.indices.map { "<sheet name=\"Tabelle\($0 + 1)\" sheetId=\"\($0 + 1)\" r:id=\"rId\($0 + 1)\"/>" }.joined() + "</sheets></workbook>"))
        files.append(("xl/_rels/workbook.xml.rels", head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + tables.indices.map { "<Relationship Id=\"rId\($0 + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet\($0 + 1).xml\"/>" }.joined()
            + "<Relationship Id=\"rId\(tables.count + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"))
        files.append(("xl/styles.xml", head + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs></styleSheet>"))
        for (i, t) in tables.enumerated() { files.append(("xl/worksheets/sheet\(i + 1).xml", sheet(t))) }
        let url = outURL(fileName(md, ext: "xlsx"))
        try StoredZip.make(files).write(to: url, options: .atomic)
        return url
    }

    // MARK: PDF

    private static func html(_ md: String) -> String {
        func inline(_ t: String) -> String {
            runs(t).map { r in
                var s = esc(r.text)
                if r.code { s = "<code>\(s)</code>" }
                if r.bold { s = "<b>\(s)</b>" }
                if r.italic { s = "<i>\(s)</i>" }
                return s
            }.joined()
        }
        var h = "<html><head><meta charset=\"utf-8\"><style>body{font-family:-apple-system,Helvetica;font-size:11pt;line-height:1.4;color:#111}h1{font-size:18pt}h2{font-size:15pt}h3{font-size:13pt}code,pre{font-family:Menlo,Courier;font-size:9pt}pre{background:#f3f3f3;padding:6pt;white-space:pre-wrap}table{border-collapse:collapse;margin:6pt 0}td,th{border:1px solid #999;padding:3pt 6pt;text-align:left}</style></head><body>"
        var inList = false
        for b in parse(md) {
            if case .item = b {} else if inList { h += "</ul>"; inList = false }
            switch b {
            case .heading(let l, let t): h += "<h\(min(l, 4))>\(inline(t))</h\(min(l, 4))>"
            case .para(let t): h += "<p>\(inline(t))</p>"
            case .item(let t, let m):
                if !inList { h += "<ul style=\"list-style:none;padding-left:12pt\">"; inList = true }
                h += "<li>\(esc(m)) \(inline(t))</li>"
            case .code(let c): h += "<pre>\(esc(c))</pre>"
            case .rule: h += "<hr/>"
            case .table(let rows):
                h += "<table>" + rows.enumerated().map { ri, row in
                    "<tr>" + row.map { ri == 0 ? "<th>\(inline($0))</th>" : "<td>\(inline($0))</td>" }.joined() + "</tr>"
                }.joined() + "</table>"
            }
        }
        if inList { h += "</ul>" }
        return h + "</body></html>"
    }

    @MainActor
    static func pdf(_ md: String) throws -> URL {
        let formatter = UIMarkupTextPrintFormatter(markupText: html(md))
        let renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(formatter, startingAtPageAt: 0)
        let page = CGRect(x: 0, y: 0, width: 595.2, height: 841.8) // A4
        let printable = page.insetBy(dx: 50, dy: 56)
        renderer.setValue(NSValue(cgRect: page), forKey: "paperRect")
        renderer.setValue(NSValue(cgRect: printable), forKey: "printableRect")
        let data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, page, nil)
        renderer.prepare(forDrawingPages: NSRange(location: 0, length: renderer.numberOfPages))
        for i in 0..<max(renderer.numberOfPages, 1) {
            UIGraphicsBeginPDFPage()
            renderer.drawPage(at: i, in: UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()
        let url = outURL(fileName(md, ext: "pdf"))
        try (data as Data).write(to: url, options: .atomic)
        return url
    }
}

/// Minimaler ZIP-Schreiber (ohne Kompression) – reicht für DOCX/XLSX.
enum StoredZip {
    private static let table: [UInt32] = (0..<256).map { i -> UInt32 in
        var c = UInt32(i)
        for _ in 0..<8 { c = (c & 1) != 0 ? 0xEDB88320 ^ (c >> 1) : c >> 1 }
        return c
    }

    static func crc32(_ data: Data) -> UInt32 {
        var c: UInt32 = 0xFFFFFFFF
        for b in data { c = table[Int((c ^ UInt32(b)) & 0xFF)] ^ (c >> 8) }
        return c ^ 0xFFFFFFFF
    }

    static func make(_ files: [(String, String)]) -> Data {
        var out = Data()
        var central = Data()
        func le16(_ v: Int) -> Data { var x = UInt16(truncatingIfNeeded: v).littleEndian; return Data(bytes: &x, count: 2) }
        func le32(_ v: UInt32) -> Data { var x = v.littleEndian; return Data(bytes: &x, count: 4) }
        for (name, content) in files {
            let nameData = Data(name.utf8)
            let data = Data(content.utf8)
            let crc = crc32(data)
            let offset = UInt32(out.count)
            // Lokaler Kopf
            out += le32(0x04034b50) + le16(20) + le16(0x0800) + le16(0) + le16(0) + le16(0x21)
            out += le32(crc) + le32(UInt32(data.count)) + le32(UInt32(data.count)) + le16(nameData.count) + le16(0)
            out += nameData + data
            // Zentralverzeichnis
            central += le32(0x02014b50) + le16(20) + le16(20) + le16(0x0800) + le16(0) + le16(0) + le16(0x21)
            central += le32(crc) + le32(UInt32(data.count)) + le32(UInt32(data.count)) + le16(nameData.count)
            central += le16(0) + le16(0) + le16(0) + le16(0) + le32(0) + le32(offset) + nameData
        }
        let cdOffset = UInt32(out.count)
        out += central
        out += le32(0x06054b50) + le16(0) + le16(0) + le16(files.count) + le16(files.count)
        out += le32(UInt32(central.count)) + le32(cdOffset) + le16(0)
        return out
    }
}
