package com.github.wwt.stockplugin.service;

import com.github.wwt.stockplugin.model.Quote;
import com.github.wwt.stockplugin.model.StockItem;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockQuoteService {
    private static final Charset GBK = Charset.forName("GBK");
    private static final Pattern TENCENT_VAR = Pattern.compile("v_[^=]+=\"([^\"]*)\"");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<StockItem> search(String keyword) throws IOException, InterruptedException {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        Map<String, StockItem> results = new LinkedHashMap<>();
        Optional<StockItem> codeItem = normalizeStock(trimmed, "");
        codeItem.ifPresent(item -> results.put(item.key(), enrichName(item)));

        String url = "https://smartbox.gtimg.cn/s3/?q=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8) + "&t=all";
        HttpRequest request = request(url).build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String body = decode(response.body());
        parseSmartbox(body).forEach(item -> results.putIfAbsent(item.key(), item));

        return new ArrayList<>(results.values());
    }

    public Quote quote(StockItem stock) {
        if (stock == null || stock.code == null || stock.code.isBlank()) {
            return Quote.empty(stock);
        }
        try {
            String query = queryCode(stock);
            String endpoint = stock.market.equalsIgnoreCase("HK") ? "https://sqt.gtimg.cn/utf8/q=" : "https://qt.gtimg.cn/q=";
            HttpResponse<byte[]> response = httpClient.send(request(endpoint + query).build(), HttpResponse.BodyHandlers.ofByteArray());
            String body = decode(response.body());
            return parseTencentQuote(stock, body);
        } catch (Exception ignored) {
            return Quote.empty(stock);
        }
    }

    public Optional<StockItem> normalizeStock(String rawCode, String rawName) {
        String text = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (text.isBlank()) {
            return Optional.empty();
        }
        text = text.replace(".", "").replace("_", "").replace("-", "");
        String name = rawName == null ? "" : rawName.trim();

        if (text.startsWith("RHK")) {
            return Optional.of(new StockItem("HK", padHongKong(text.substring(3)), name));
        }
        if (text.startsWith("HK")) {
            return Optional.of(new StockItem("HK", padHongKong(text.substring(2)), name));
        }
        if (text.startsWith("SH") && text.length() >= 8) {
            return Optional.of(new StockItem("SH", text.substring(2), name));
        }
        if (text.startsWith("SZ") && text.length() >= 8) {
            return Optional.of(new StockItem("SZ", text.substring(2), name));
        }
        if (text.startsWith("BJ") && text.length() >= 8) {
            return Optional.of(new StockItem("BJ", text.substring(2), name));
        }

        Matcher matcher = DIGITS.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        if (text.length() <= 5) {
            return Optional.of(new StockItem("HK", padHongKong(text), name));
        }
        if (text.startsWith("6") || text.startsWith("5") || text.startsWith("51") || text.startsWith("56") || text.startsWith("58")) {
            return Optional.of(new StockItem("SH", text, name));
        }
        if (text.startsWith("8") || text.startsWith("4")) {
            return Optional.of(new StockItem("BJ", text, name));
        }
        return Optional.of(new StockItem("SZ", text, name));
    }

    private StockItem enrichName(StockItem item) {
        Quote quote = quote(item);
        if (quote.stock != null && quote.stock.name != null && !quote.stock.name.isBlank()) {
            item.name = quote.stock.name;
        }
        return item;
    }

    private List<StockItem> parseSmartbox(String body) {
        List<StockItem> items = new ArrayList<>();
        String normalized = body.replace("\\n", "\n").replace("^", "\n").replace("|", "\n");
        for (String line : normalized.split("\\R")) {
            String[] parts = line.replace("\"", "").split("~");
            if (parts.length < 3) {
                continue;
            }
            for (int index = 0; index < parts.length; index++) {
                String token = parts[index].trim();
                Optional<StockItem> maybe = normalizeStock(token, index + 1 < parts.length ? parts[index + 1] : "");
                if (maybe.isPresent()) {
                    StockItem item = maybe.get();
                    if (item.name.isBlank()) {
                        item.name = findLikelyName(parts, index);
                    }
                    if (!item.name.isBlank()) {
                        items.add(item);
                    }
                }
            }
        }
        return items;
    }

    private String findLikelyName(String[] parts, int codeIndex) {
        for (int i = Math.max(0, codeIndex - 2); i < Math.min(parts.length, codeIndex + 4); i++) {
            String candidate = parts[i].trim();
            if (!candidate.isBlank() && !DIGITS.matcher(candidate).matches() && !candidate.matches("[A-Z]{1,4}")) {
                return candidate;
            }
        }
        return "";
    }

    private Quote parseTencentQuote(StockItem requested, String body) {
        Matcher matcher = TENCENT_VAR.matcher(body);
        if (!matcher.find()) {
            return Quote.empty(requested);
        }
        String[] fields = matcher.group(1).split("~", -1);
        if (fields.length < 4) {
            return Quote.empty(requested);
        }

        String name = firstNonBlank(valueAt(fields, 1), requested.name);
        String code = firstNonBlank(valueAt(fields, 2), requested.code);
        StockItem stock = new StockItem(requested.market, normalizeReturnedCode(requested.market, code), name);
        double price = number(valueAt(fields, 3));
        double previousClose = number(valueAt(fields, 4));
        double openPrice = firstFinite(number(valueAt(fields, 5)), previousClose);
        double changePercent = firstFinite(
                number(valueAt(fields, 32)),
                previousClose > 0 && Double.isFinite(price) ? (price - previousClose) / previousClose * 100 : Double.NaN
        );
        double amount = firstFinite(number(valueAt(fields, 38)), number(valueAt(fields, 37)));
        if (!"HK".equalsIgnoreCase(requested.market) && Double.isFinite(amount)) {
            amount *= 10000;
        }
        return new Quote(stock, price, round(changePercent, 2), amount, previousClose, openPrice);
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 StockMonitor/0.1")
                .header("Referer", "https://finance.qq.com/");
    }

    private String queryCode(StockItem stock) {
        if ("HK".equalsIgnoreCase(stock.market)) {
            return "r_hk" + padHongKong(stock.code);
        }
        return stock.market.toLowerCase(Locale.ROOT) + stock.code;
    }

    private String normalizeReturnedCode(String market, String code) {
        if ("HK".equalsIgnoreCase(market)) {
            return padHongKong(code);
        }
        return code == null ? "" : code.trim();
    }

    private String padHongKong(String code) {
        String digits = code == null ? "" : code.replaceAll("\\D", "");
        if (digits.length() >= 5) {
            return digits.substring(digits.length() - 5);
        }
        return String.format("%05d", digits.isBlank() ? 0 : Integer.parseInt(digits));
    }

    private String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.contains("�")) {
            return unescapeUnicode(new String(bytes, GBK));
        }
        return unescapeUnicode(utf8);
    }

    private String unescapeUnicode(String input) {
        Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            int codePoint = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(String.valueOf((char) codePoint)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String valueAt(String[] fields, int index) {
        return index >= 0 && index < fields.length ? fields[index].trim() : "";
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? (fallback == null ? "" : fallback) : first;
    }

    private double number(String raw) {
        if (raw == null || raw.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private double firstFinite(double... values) {
        for (double value : values) {
            if (Double.isFinite(value)) {
                return value;
            }
        }
        return Double.NaN;
    }

    private double round(double value, int scale) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
