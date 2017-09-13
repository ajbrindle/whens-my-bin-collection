package com.sk7software.bincollection.model;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BinTest {

    @Test
    public void testCollectedOnDate() {
        Bin b = new Bin();
        b.setColour("blue");
        b.setDate(new DateTime(2017, 5, 30, 0, 0));
        assertTrue(b.isCollectedOnDate(new DateTime(2017,5,30,10,30)));
        assertFalse(b.isCollectedOnDate(new DateTime(2017, 5, 29, 10, 0)));
    }

    @Test
    public void testBinList1Bin() {
        List<Bin> bl = new ArrayList<>();
        bl.add(new Bin("blue", new DateTime()));

        assertEquals(Bin.getSpokenBinList(bl), "The blue bin");
    }

    @Test
    public void testBinList2Bins() {
        List<Bin> bl = new ArrayList<>();
        bl.add(new Bin("blue", new DateTime()));
        bl.add(new Bin("red", new DateTime()));

        assertEquals(Bin.getSpokenBinList(bl), "The blue and red bins");
    }

    @Test
    public void testBinList3Bins() {
        List<Bin> bl = new ArrayList<>();
        bl.add(new Bin("blue", new DateTime()));
        bl.add(new Bin("red", new DateTime()));
        bl.add(new Bin("yellow", new DateTime()));

        assertEquals(Bin.getSpokenBinList(bl), "The blue, red and yellow bins");
    }

    @Test
    public void testBinList4Bins() {
        List<Bin> bl = new ArrayList<>();
        bl.add(new Bin("blue", new DateTime()));
        bl.add(new Bin("red", new DateTime()));
        bl.add(new Bin("yellow", new DateTime()));
        bl.add(new Bin("green", new DateTime()));

        assertEquals(Bin.getSpokenBinList(bl), "The blue, red, yellow and green bins");
    }

}
