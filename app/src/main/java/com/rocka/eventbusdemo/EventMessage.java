package com.rocka.eventbusdemo;

/**
 * @author: Rocka
 * @version: 1.0
 * @description: TODO
 * @time:2018/10/17
 */
public class EventMessage {
    int num;

    public EventMessage(int num) {
        this.num = num;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }
}
