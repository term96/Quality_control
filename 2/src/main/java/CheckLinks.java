import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class CheckLinks {
    private static final String HINT = "Формат: CheckLinks <URL сайта>";
    private static final String ALL_FILENAME = "all_links.txt";
    private static final String CORRUPTED_FILENAME = "corrupted_links.txt";
    private static final String MALFORMED_URL = "Некорректный URL-адрес";
    private static final String OPEN_CONNECTION_ERROR = "Соединение не установлено";
    private static final String ARGUMENT_COUNT_ERROR = "Неверное количество аргументов";
    private static final String CLOSE_ERROR = "Ошибка при закрытии лог-файлов";
    private static final String CREATE_ERROR = "Ошибка создания лог-файлов";
    private static final String WRITE_ERROR = "Ошибка записи в лог-файл";
    private static final String CHECKING = "Проверка ссылки №";
    private static final String FINAL_TIME = "Время завершения проверки: ";
    private static final String ALL_COUNT = "Количество ссылок: ";
    private static final String CORRUPTED_COUNT = "Количество битых ссылок: ";
    private static final String DONE = "Проверка завершена";
    private static final String MIME = "Ссылка содержит MIME-тип ";
    private static final String TIMEOUT = "Превышено время ожидания";
    private static final int NOT_HTTP = -1;
    private static final int BAD_REQUEST_CODE = 400;

    private static BufferedWriter allUrlsFile;
    private static BufferedWriter corruptedUrlsFile;
    private static HashSet<String> checkedUrls = new HashSet<>();
    private static ArrayList<String> uncheckedUrls = new ArrayList<>();
    private static String baseHost;
    private static int urlCount = 0;
    private static int corruptedUrlCount = 0;

    public static void main(String[] args) {
        if (args.length != 1) {
            exitWithError(ARGUMENT_COUNT_ERROR);
        }

        try {
            baseHost = new URL(args[0].toLowerCase()).getHost();
        } catch (MalformedURLException e) {
            exitWithError(MALFORMED_URL);
        }

        if (baseHost == null || baseHost.isEmpty()) {
            exitWithError(MALFORMED_URL);
        }

        createFiles();
        uncheckedUrls.add(args[0].toLowerCase());

        for (int i = 0; i < uncheckedUrls.size(); i++) {
            if (i == 50) {
                break;
            }
            System.out.println(CHECKING + (i + 1));
            checkUrl(uncheckedUrls.get(i));
        }

        System.out.println(DONE);
        writeFinalInfo();
        closeFiles();
    }

    private static void checkUrl(final String fullUrl) {
        Document document = null;
        int responseCode = NOT_HTTP;
        String responseMessage;

        try {
            Connection connection = Jsoup.connect(fullUrl);
            document = connection.ignoreHttpErrors(true).get();
            responseMessage = connection.response().statusMessage();
            responseCode = connection.response().statusCode();
        } catch (MalformedURLException e) {
            responseMessage = MALFORMED_URL;
        } catch (UnsupportedMimeTypeException e) {
            responseMessage = MIME + e.getMimeType();
            writeUrl(fullUrl, responseMessage);
            return;
        } catch (SocketTimeoutException e) {
            responseMessage = TIMEOUT;
        } catch (IOException e) {
            responseMessage = OPEN_CONNECTION_ERROR;
        }

        final String status = (responseCode == NOT_HTTP) ? responseMessage : responseCode + " " + responseMessage;
        writeUrl(fullUrl, status);
        if (responseCode == NOT_HTTP || responseCode >= BAD_REQUEST_CODE || document == null) {
            writeCorruptedUrl(fullUrl, status);
            return;
        }

        Elements elements = document.select("a[href]");
        for (Element element : elements) {
            final String href = element.attr("abs:href");
            if (href == null || href.isEmpty()) {
                continue;
            }

            URL hrefUrl;
            try {
                hrefUrl = new URL(href);
            } catch (MalformedURLException e) {
                writeUrl(href, MALFORMED_URL);
                writeCorruptedUrl(href, MALFORMED_URL);
                continue;
            }

            if (!hrefUrl.getHost().contains(baseHost)) {
                continue;
            }

            if (!checkedUrls.contains(hrefUrl.toString())) {
                uncheckedUrls.add(hrefUrl.toString());
                checkedUrls.add(hrefUrl.toString());
            }
        }
    }

    private static void closeFiles() {
        try {
            allUrlsFile.close();
            corruptedUrlsFile.close();
        } catch (IOException e) {
            System.out.println(CLOSE_ERROR);
            System.exit(1);
        }
    }

    private static void createFiles() {
        try {
            allUrlsFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ALL_FILENAME)));
            corruptedUrlsFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(CORRUPTED_FILENAME)));
        } catch (FileNotFoundException e) {
            System.out.println(CREATE_ERROR);
            System.exit(1);
        }
    }

    private static void writeUrl(final String url, final String status) {
        urlCount++;
        checkedUrls.add(url);
        try {
            allUrlsFile.write(url + " - " + status);
            allUrlsFile.newLine();
        } catch (IOException e) {
            System.out.println(WRITE_ERROR);
            System.exit(1);
        }
    }

    private static void writeCorruptedUrl(final String url, final String status) {
        corruptedUrlCount++;
        checkedUrls.add(url);
        try {
            corruptedUrlsFile.write(url + " - " + status);
            corruptedUrlsFile.newLine();
        } catch (IOException e) {
            System.out.println(WRITE_ERROR);
            System.exit(1);
        }
    }

    private static void writeFinalInfo() {
        final String time = FINAL_TIME + LocalDate.now().toString() + " " + LocalTime.now().toString();
        final String all = ALL_COUNT + urlCount;
        final String corrupted = CORRUPTED_COUNT + corruptedUrlCount;
        try {
            allUrlsFile.write(all);
            allUrlsFile.newLine();
            allUrlsFile.write(time);
            allUrlsFile.newLine();
            corruptedUrlsFile.write(corrupted);
            corruptedUrlsFile.newLine();
            corruptedUrlsFile.write(time);
            corruptedUrlsFile.newLine();
        } catch (IOException e) {
            System.out.println(WRITE_ERROR);
            System.exit(1);
        }
    }

    private static void exitWithError(final String error) {
        System.out.println(error);
        System.out.println(HINT);
        System.exit(1);
    }
}
