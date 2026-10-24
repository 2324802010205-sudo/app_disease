package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

public class Post {
    public String id;
    public PublicUser author;
    public String province;
    public String topic;
    public String title;
    public String content;
    public List<ImageRef> images = new ArrayList<>();
    public long likeCount;
    public long commentCount;
    public boolean liked;
    public boolean saved;
    public boolean mine;
    public String shareUrl;
    public long createdAt;
    public long updatedAt;

    public static class Draft {
        public String id;
        public String topic;
        public String title;
        public String content;
        public List<ImageRef> images = new ArrayList<>();
        public long createdAt;
        public long updatedAt;
    }

    public static class LikeResult {
        public boolean liked;
        public long likeCount;
    }
}
