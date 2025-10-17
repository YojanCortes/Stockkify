// path: src/main/java/com/inventario1/Inventario/util/csv/FlexibleCsvReader.java
package com.inventario1.Inventario.util.csv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Consumer;

/** CSV tolerante a delimitador (',' ';' '\t'), BOM UTF-8, alias y acentos. */
public class FlexibleCsvReader {
    private final Map<String,String> headerAliases = new HashMap<>();
    private final Set<String> required = new LinkedHashSet<>();
    private final List<String> warnings = new ArrayList<>();
    private List<String> lastHeaders = List.of();

    public void addHeaderAlias(String alias, String canonical){
        headerAliases.put(normalizeHeader(alias), canonical);
    }
    public void setRequiredHeaders(Collection<String> names){
        required.clear(); required.addAll(names);
    }
    public List<String> getWarnings(){ return warnings; }
    public List<String> getLastHeaders(){ return lastHeaders; }

    public static final class CsvRow {
        public final long lineNumber; public final Map<String,String> values;
        public CsvRow(long lineNumber, Map<String,String> values){ this.lineNumber=lineNumber; this.values=values; }
        public String get(String name){ return values.get(name); }
    }

    public void read(InputStream input, Consumer<CsvRow> rowConsumer) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stripBOM(input), StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("Archivo vacío");

            char delimiter = detectDelimiter(headerLine);
            List<String> rawHeaders = splitCsvLine(headerLine, delimiter);

            List<String> headers = new ArrayList<>(rawHeaders.size());
            for (String h : rawHeaders) {
                String norm = normalizeHeader(h);
                headers.add(headerAliases.getOrDefault(norm, norm));
            }
            this.lastHeaders = List.copyOf(headers);

            for (String req : required) {
                if (!headers.contains(req)) {
                    throw new IOException("Falta columna requerida: " + req + " (encabezados: " + String.join(", ", headers) + ")");
                }
            }

            String line; long lineNo = 1;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) continue;
                List<String> values = splitCsvLine(line, delimiter);
                if (values.size() < headers.size()) {
                    while (values.size() < headers.size()) values.add(null);
                } else if (values.size() > headers.size()) {
                    warnings.add("Línea " + lineNo + ": " + values.size() + " columnas; se ignorarán extras.");
                }
                Map<String,String> row = new LinkedHashMap<>();
                for (int i=0;i<headers.size();i++){
                    String key = headers.get(i);
                    String val = i<values.size()? trimQuotes(values.get(i)) : null;
                    row.put(key, emptyToNull(val));
                }
                rowConsumer.accept(new CsvRow(lineNo, row));
            }
        }
    }

    // --- helpers (solo por "por qué": robustez ante archivos variados) ---
    private static InputStream stripBOM(InputStream in) throws IOException {
        PushbackInputStream pb = new PushbackInputStream(in, 3);
        byte[] bom = new byte[3]; int n = pb.read(bom,0,3);
        if (!(n==3 && (bom[0]&0xFF)==0xEF && (bom[1]&0xFF)==0xBB && (bom[2]&0xFF)==0xBF)) { if (n>0) pb.unread(bom,0,n); }
        return pb;
    }
    private static char detectDelimiter(String s){
        int c = count(s,','), sc = count(s,';'), t = count(s,'\t');
        if (sc>=c && sc>=t) return ';'; if (t>=c && t>=sc) return '\t'; return ',';
    }
    private static int count(String s, char ch){ int k=0; for(char c: s.toCharArray()) if(c==ch) k++; return k; }
    private static String normalizeHeader(String s){
        if(s==null) return "";
        String t = s.trim();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+","");
        t = t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");
        return t.replaceAll("_{2,}","_");
    }
    private static String trimQuotes(String s){
        if (s==null) return null; String t=s.trim();
        if (t.length()>=2 && t.startsWith("\"") && t.endsWith("\"")) t=t.substring(1,t.length()-1);
        return t.trim();
    }
    private static String emptyToNull(String s){ return (s==null||s.trim().isEmpty())?null:s; }
    private static List<String> splitCsvLine(String line, char delimiter){
        List<String> out = new ArrayList<>(); StringBuilder cur = new StringBuilder(); boolean inQ=false;
        for (int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if (ch=='"'){ if (inQ && i+1<line.length() && line.charAt(i+1)=='"'){ cur.append('"'); i++; } else inQ=!inQ; }
            else if (ch==delimiter && !inQ){ out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }
}
