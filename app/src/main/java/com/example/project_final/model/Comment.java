package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

public class Comment {
    public String id;
    public String parentId;
    public PublicUser author;
    public String text;
    public long likeCount;
    public boolean liked;
    public boolean mine;
    public String badge; // AUTHOR | EXPERT | null
    public long createdAt;
    public List<Comment> replies = new ArrayList<>();
}
