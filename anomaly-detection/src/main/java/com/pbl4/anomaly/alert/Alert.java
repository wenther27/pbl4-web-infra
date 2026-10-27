package com.pbl4.anomaly.alert;

// Mot canh bao do mot luat tao ra.
// record: lop chi chua du lieu, Java tu sinh constructor, getter, equals, toString.
public record Alert(
        String rule,       // ma luat, vd HIGH_RATE_IP
        String severity,   // HIGH / MEDIUM
        String target,     // doi tuong bi canh bao: IP, backend, hoac "global"
        String message,    // cau mo ta cho nguoi doc
        double value,      // gia tri do duoc, vd 412 request
        double threshold   // nguong da vuot, vd 100
) {
    // Khoa de chong bao trung o buoi 3: cung luat + cung doi tuong = cung mot su co
    public String key() {
        return rule + "|" + target;
    }
}