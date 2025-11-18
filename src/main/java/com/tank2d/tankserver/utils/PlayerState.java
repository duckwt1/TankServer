package com.tank2d.shared;

import java.io.Serializable;

public class PlayerState implements Serializable {
    public String userName;
    public double x;
    public double y;
    public double bodyAngle;
    public double gunAngle;
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean backward;
    //public boolean isMoving;

    public PlayerState(String userName, double x, double y, double bodyAngle, double gunAngle,
                       boolean up, boolean down, boolean left, boolean right, boolean backward) {
        this.userName = userName;
        this.x = x;
        this.y = y;
        this.bodyAngle = bodyAngle;
        this.gunAngle = gunAngle;
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.backward = backward;
        //this.isMoving = isMoving;
    }

    public PlayerState() {}
}
