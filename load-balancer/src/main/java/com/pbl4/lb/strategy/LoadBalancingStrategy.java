package com.pbl4.lb.strategy;

import java.util.List;

//Cach chon backend cho moi request
public interface LoadBalancingStrategy {
    //Chon 1 BE trong danh sach
    String next(List<String> backends);

    //ten thuat toan dung khi ghi log va so sanh
    String name();
}

//Day la strategy pattern