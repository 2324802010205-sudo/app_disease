package com.riceguard.api.common;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Component;

/** Bản đồ cấu trúc Firestore. Dữ liệu riêng của người dùng nằm dưới users/{uid}/... */
@Component
public class Refs {
    private final Firestore db;

    public Refs(Firestore db) {
        this.db = db;
    }

    public CollectionReference users() { return db.collection("users"); }
    public DocumentReference user(String uid) { return users().document(uid); }
    public CollectionReference fields(String uid) { return user(uid).collection("fields"); }
    public CollectionReference scans(String uid) { return user(uid).collection("scans"); }
    public CollectionReference diaries(String uid) { return user(uid).collection("diaries"); }
    public CollectionReference notifications(String uid) { return user(uid).collection("notifications"); }
    public CollectionReference savedPosts(String uid) { return user(uid).collection("savedPosts"); }
    public CollectionReference savedDiseases(String uid) { return user(uid).collection("savedDiseases"); }
    public CollectionReference drafts(String uid) { return user(uid).collection("drafts"); }

    public CollectionReference diseases() { return db.collection("diseases"); }
    public CollectionReference posts() { return db.collection("posts"); }
    public CollectionReference comments(String postId) { return posts().document(postId).collection("comments"); }
    public CollectionReference likes(String postId) { return posts().document(postId).collection("likes"); }
    public CollectionReference commentLikes(String postId, String commentId) { return comments(postId).document(commentId).collection("likes"); }
    public CollectionReference areaStats() { return db.collection("areaStats"); }
    public CollectionReference feedbacks() { return db.collection("feedbacks"); }
    public CollectionReference reports() { return db.collection("reports"); }
    public CollectionReference locks() { return db.collection("locks"); }
}
