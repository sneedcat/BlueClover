package org.floens.chan.core.site.sites.kun8;

import android.util.JsonReader;
import android.util.Log;

import com.android.volley.Response;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.Site;
import org.floens.chan.utils.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Kun8BoardsRequest extends JsonReaderRequest<List<Board>> {
    private final Site site;

    public Kun8BoardsRequest(Site site, Response.Listener<List<Board>> listener, Response.ErrorListener errorListener) {
        super(site.endpoints().boards().toString(), listener, errorListener);
        this.site = site;
    }

    @Override
    public List<Board> readJson(JsonReader reader) throws Exception {
        List<Board> list = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            while (reader.hasNext()) {
                Board board = readBoardEntry(reader);
                if (board != null) {
                    list.add(board);
                }
            }
        }
        reader.endArray();
        return list;
    }

    private Board readBoardEntry(JsonReader reader) throws IOException {
        reader.beginObject();

        Board board = new Board();
        board.siteId = site.id();
        board.site = site;
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "title":
                    board.name = reader.nextString();
                    break;
                case "description":
                    board.description = reader.nextName();
                    break;
                case "uri":
                    board.code = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (!board.finish()) {
            // Invalid data, ignore
            return null;
        }
        return board;
    }
}
