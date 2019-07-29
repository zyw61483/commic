package entity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comic
 *
 * @author: zhaoyiwei
 * @date: 2019/7/24 11:29
 */
@Slf4j
@Data
public abstract class Comic {
    private Integer startChapter = 0;
    private Integer endChapter = 1;
    private String chapterContent;
    private ExecutorService threadPool = Executors.newFixedThreadPool(15);
    private CompletionService<Boolean> completionService = new ExecutorCompletionService<>(threadPool);

    public abstract List<ChapterIndex> getChapterIndexUrls(String content);

    public abstract Chapter getChapter(String content, String chapterName);

    public void download(String chapterIndexUrl, Integer startChapter, Integer endChapter) throws Exception {
        // 漫画目录页初始化
        this.init(chapterIndexUrl, startChapter, endChapter);
        // 获取章节信息
        List<ChapterIndex> chapterIndex = this.getChapterIndexUrls(this.chapterContent);
        int picCounts = 0;
        for (ChapterIndex index : chapterIndex) {
            if (isDownloadThisChapter(index)) {
                log.info("ChapterIndex:{}", index);
                HtmlPage chapterPage = new HtmlPage(index.getUrl(), true);
                Chapter chapterInfo = this.getChapter(chapterPage.getContent(), index.getName());
                picCounts += downloadChapter(chapterInfo);
            }
        }
        this.showProgress(picCounts);
        this.shutdown();
    }

    private void init(String chapterIndexUrl, Integer startChapter, Integer endChapter) throws IOException {
        HtmlPage chapterIndexPage = new HtmlPage(chapterIndexUrl, true);
        this.chapterContent = chapterIndexPage.getContent();
        this.startChapter = startChapter;
        this.endChapter = endChapter;
    }

    private int downloadChapter(Chapter chapterInfo) {
        List<String> picUrls = chapterInfo.getPicUrls();
        for (int i = 0; i < picUrls.size(); i++) {
            String tempUrl = picUrls.get(i);
            String picUrl = tempUrl.trim().replaceAll(" ", "%20");
            int name = i;
            this.getThreadPool().submit(() -> {
                try {
                    HtmlPage picPage = new HtmlPage(picUrl, false);
                    HttpEntity entity = picPage.getEntity();
                    String pathName = "/pic1/" + chapterInfo.getCommicName() + "/" + chapterInfo.getChapterName();
                    File dic = new File(pathName);
                    if (!dic.exists()) {
                        dic.mkdirs();
                    }
                    File file = new File(pathName + getBtName(name) + ".jpg");
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    FileOutputStream out = new FileOutputStream(file);
                    InputStream in = entity.getContent();
                    byte[] buffer = new byte[4096];
                    int readLength = 0;
                    while ((readLength = in.read(buffer)) > 0) {
                        byte[] bytes = new byte[readLength];
                        System.arraycopy(buffer, 0, bytes, 0, readLength);
                        out.write(bytes);
                    }
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    log.error("download error", e);
                    return false;
                }
                return true;
            });
        }
        return picUrls.size();
    }

    private boolean isDownloadThisChapter(ChapterIndex index) {
        Pattern r = Pattern.compile("第(.*?)话");
        Matcher m = r.matcher(index.getName());
        boolean isDownload = false;
        if (m.find()) {
            int huaNum = 0;
            try {
                huaNum = Integer.parseInt(m.group(1));
                isDownload = huaNum >= getStartChapter() && huaNum <= getEndChapter();
            } catch (Exception e) {
            }
        }
        return isDownload;
    }

    private CompletionService<Boolean> getThreadPool() {
        return completionService;
    }

    private void showProgress(int picCounts) throws Exception {
        for (int i = 1; i < picCounts; i++) {
            if (getThreadPool().take().get()) {
                DecimalFormat df = new DecimalFormat("0.00%");
                log.info("sum:{}，num:{} {}", picCounts - 1, i, df.format((float) i / (picCounts - 1)));
            }
        }
        log.info("download success");
    }

    private void shutdown() {
        threadPool.shutdown();
    }

    private String getBtName(int i) {
        int a = i / 26;
        int y = i % 26;
        StringBuffer name = new StringBuffer("/");
        for (int j = 0; j < a; j++) {
            name.append("z");
        }
        name.append((char) (y + 97));
        return name.toString();
    }
}
