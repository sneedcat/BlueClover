package org.floens.chan.core.site.sites.kun8;

import android.util.Log;

import androidx.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Boards;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.MultipartHttpCall;
import org.floens.chan.core.site.common.vichan.VichanActions;
import org.floens.chan.core.site.common.vichan.VichanApi;
import org.floens.chan.core.site.common.vichan.VichanCommentParser;
import org.floens.chan.core.site.common.vichan.VichanEndpoints;
import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.sites.dvach.Dvach;
import org.floens.chan.core.site.sites.dvach.DvachBoardsRequest;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

public class Kun8 extends CommonSite {
    private static final String TAG = "Kun8";
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Kun8.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://8kun.top/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"8chan", "8kun", "8ch"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode).addPathSegment("res")
                        .addPathSegment(String.valueOf(loadable.no) + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("8kun");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://media.128ducks.com/static/favicon.ico")));
        setBoardsType(BoardsType.DYNAMIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://8kun.top",
                "https://sys.8kun.top") {

            private final HttpUrl i = new HttpUrl.Builder()
                    .scheme("https")
                    .host("media.128ducks.com")
                    .build();

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return i.newBuilder()
                        .addPathSegment("file_store")
                        .addPathSegment(arg.get("tim") + "." + arg.get("ext"))
                        .build();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                String ext;
                switch (arg.get("ext")) {
                    case "jpeg":
                    case "jpg":
                    case "png":
                    case "gif":
                        ext = arg.get("ext");
                        break;
                    default:
                        ext = "jpg";
                        break;
                }

                return i.newBuilder()
                        .addPathSegment("file_store")
                        .addPathSegment("thumb")
                        .addPathSegment(arg.get("tim") + "." + ext)
                        .build();
            }
        });
        setActions(new VichanActions(this) {
            @Override
            public void setupPost(Reply reply, MultipartHttpCall call) {
                super.setupPost(reply, call);

                if (reply.loadable.isThreadMode()) {
                    // "thread" is already added in VichanActions.
                    call.parameter("post", "New Reply");
                } else {
                    call.parameter("post", "New Thread");
                    call.parameter("page", "1");
                }
            }

            @Override
            public boolean requirePrepare() {
                // We don't need to check the antispam fields for 8chan.
                return false;
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromUrl("https://sys.8kun.top/dnsbls_bypass.php",
                        "You failed the CAPTCHA",
                        "You may now go back and make your post");
            }

            @Override
            public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {
                super.delete(deleteRequest, deleteListener);
            }
            @Override
            public void boards(final BoardsListener listener) {
                requestQueue.add(new Kun8BoardsRequest(Kun8.this, response -> {
                    listener.onBoardsReceived(new Boards(response));
                }, (error) -> {
                    Logger.e(TAG, "Failed to get boards from server", error);

                    // API fail, provide some default boards
                    List<Board> list = new ArrayList<>();
                    list.add(Board.fromSiteNameCode(Kun8.this, "бред", "b"));
                    list.add(Board.fromSiteNameCode(Kun8.this, "Видеоигры, general, официальные треды", "vg"));
                    list.add(Board.fromSiteNameCode(Kun8.this, "новости", "news"));
                    list.add(Board.fromSiteNameCode(Kun8.this, "политика, новости, ольгинцы, хохлы, либерахи, рептилоиды.. oh shi", "po"));
                    Collections.shuffle(list);
                    listener.onBoardsReceived(new Boards(list));
                }));
            }

        });

        setApi(new VichanApi(this));

        setParser(new VichanCommentParser());
    }
}