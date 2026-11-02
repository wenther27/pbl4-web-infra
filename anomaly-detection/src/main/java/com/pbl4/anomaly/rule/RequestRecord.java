package com.pbl4.anomaly.rule;

// Mot request le (khong phai so dem) lay tu log cua LB.
// R1-R7 chi can so dem (aggregation); R8 can xem NOI DUNG tung request nen can ban ghi nay.
public record RequestRecord(
        String timestamp,
        String clientIp,
        String path,       // vd /api/movies
        String query,      // phan sau dau ?, nguyen van client gui (con ma hoa %27...)
        String userAgent,
        int status
) {}
