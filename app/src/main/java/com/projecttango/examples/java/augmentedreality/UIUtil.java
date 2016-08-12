package com.projecttango.examples.java.augmentedreality;

import android.graphics.Color;
import android.graphics.Point;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.Matrix;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.scene.RajawaliScene;

import java.util.Stack;

/**
 * Created by CGV02 on 2016/8/12.
 */
public class UIUtil {
    static class Rect
    {
        final static public float DEPTH = 0.5f;
        private Vector3 p1, p2;
        private Line3D[] lines = new Line3D[4];
        private Vector3[] vertices = new Vector3[4];

        public Rect(Vector2 p1, Vector2 p2)
        {
            this.p1 = new Vector3(p1.getX(), p1.getY(), DEPTH);
            this.p2 = new Vector3(p2.getX(), p2.getY(), DEPTH);

            vertices[0] = this.p1;
            vertices[1] = new Vector3(p2.getX(), p1.getY(), DEPTH);
            vertices[2] = this.p2;
            vertices[3] = new Vector3(p1.getX(), p2.getY(), DEPTH);

            for(int i = 0; i < 4; i++) {
                lines[i] =  create3DLine(vertices[i], vertices[(i + 1) % 4]);
            }
        }

        public void addToScene(RajawaliScene scene)
        {
            for(Line3D line : lines) {
                scene.addChild(line);
            }
        }

        public void update(Vector3 translation)
        {
        }
    }

    static public Line3D create3DLine(Vector3 p1, Vector3 p2)
    {
        Stack points = new Stack();
        points.push(p1);
        points.push(p2);

        Line3D line = new Line3D(points, 5, 0xff00ff00);
        Material material = new Material();
        material.setColor(Color.GREEN);
        line.setMaterial(material);
        return line;
    }
}
