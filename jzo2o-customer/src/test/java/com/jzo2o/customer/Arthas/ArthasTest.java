package com.jzo2o.customer.Arthas;

public class ArthasTest {

    public static void main(String[] args) {

        int a = 1;

        new Thread(() -> {
            while (true) {
                System.out.println(1);
            }
        }
        ).start();

    }

}
