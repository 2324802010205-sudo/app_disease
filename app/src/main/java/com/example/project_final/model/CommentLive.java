package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Lô thay đổi bình luận từ kênh thời gian thực. reset=true: lỡ sự kiện, cần tải lại toàn bộ bình luận. */
public class CommentLive {
    public long latest;
    public boolean reset;
    public List<Comment> comments = new ArrayList<>();
    public List<String> removed = new ArrayList<>();
}
