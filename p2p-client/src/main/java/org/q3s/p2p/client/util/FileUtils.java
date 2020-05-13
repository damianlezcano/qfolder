package org.q3s.p2p.client.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.q3s.p2p.model.QFile;

public class FileUtils {

    public static List<QFile> files(String path) {
        List<QFile> res = new ArrayList<QFile>();
        File folder = new File(path);
        File[] files = folder.listFiles();
        for (File file : files) {
            QFile f = new QFile();
            f.setName(file.getName());
            f.setDate(file.lastModified());
            f.setSize(file.length());
            res.add(f);
        }
        return res;
    }

}
