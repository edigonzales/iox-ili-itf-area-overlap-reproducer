package ch.interlis.reproducer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import ch.ehi.iox.objpool.ObjectPoolManager;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.itf.impl.ItfAreaPolygon2Linetable;
import ch.interlis.iom_j.itf.impl.ItfAreaPolygon2LinetableFixed;
import ch.interlis.iom_j.itf.impl.jtsext.algorithm.CurveSegmentIntersector;
import ch.interlis.iom_j.itf.impl.jtsext.geom.ArcSegment;
import ch.interlis.iom_j.itf.impl.jtsext.geom.StraightSegment;
import ch.interlis.iox.IoxException;

public class ItfAreaOverlapReproducerTest {

    private static final double DMAV_MAX_OVERLAP = 0.002;

    private static final Coordinate ARC_START = new Coordinate(2754998.564, 1260356.465);
    private static final Coordinate ARC_POINT = new Coordinate(2754997.839, 1260355.844);
    private static final Coordinate COMMON_POINT = new Coordinate(2754997.727, 1260354.897);
    private static final Coordinate STRAIGHT_END = new Coordinate(2754996.744, 1260358.680);

    @Test
    public void arcStraightOverlapIsWithinDmavTolerance() {
        CurveSegmentIntersector intersector = new CurveSegmentIntersector();
        ArcSegment arc = new ArcSegment(ARC_START, ARC_POINT, COMMON_POINT);
        StraightSegment straight = new StraightSegment(COMMON_POINT, STRAIGHT_END);

        intersector.computeIntersection(arc, straight);

        assertTrue(intersector.hasIntersection());
        assertEquals(2, intersector.getIntersectionNum());
        assertNotNull(intersector.getOverlap());
        assertEquals(3.894404398963047E-8, intersector.getOverlap(), 1E-15);
        assertEquals(2754997.7268392625, intersector.getIntersection(0).x, 1E-10);
        assertEquals(1260354.8976185862, intersector.getIntersection(0).y, 1E-10);
        assertTrue("overlap should be below DMAV tolerance, but was " + intersector.getOverlap(),
                intersector.getOverlap() < DMAV_MAX_OVERLAP);

        System.out.println("intersection 1: " + intersector.getIntersection(0));
        System.out.println("intersection 2: " + intersector.getIntersection(1));
        System.out.println("INTERLIS overlap: " + intersector.getOverlap());
        System.out.println("DMAV max overlap: " + DMAV_MAX_OVERLAP);
    }

    @Test
    public void upstreamItfAreaWriterRejectsToleranceValidLinework() throws Exception {
        ObjectPoolManager objectPoolManager = new ObjectPoolManager();
        try {
            ItfAreaPolygon2Linetable builder = new ItfAreaPolygon2Linetable(
                    "Reproducer.Topic.Area", objectPoolManager);
            builder.addLines("1", null, createProblemLines());

            try {
                builder.getLines();
                fail("Expected current iox-ili behavior: IoxException: intersections");
            } catch (IoxException e) {
                assertEquals("intersections", e.getMessage());
                System.out.println("UPSTREAM: rejected tolerance-valid linework: " + e.getMessage());
            }
        } finally {
            objectPoolManager.close();
        }
    }

    @Test
    public void fixedItfAreaWriterAcceptsToleranceValidLinework() throws Exception {
        ObjectPoolManager objectPoolManager = new ObjectPoolManager();
        try {
            ItfAreaPolygon2LinetableFixed builder = new ItfAreaPolygon2LinetableFixed(
                    objectPoolManager, DMAV_MAX_OVERLAP);
            builder.addLines("1", null, createProblemLines());

            List<IomObject> outputLines = builder.getLines();

            assertNotNull(outputLines);
            assertEquals(2, outputLines.size());
            System.out.println("FIXED: accepted same linework with maxOverlap=" + DMAV_MAX_OVERLAP);
        } finally {
            objectPoolManager.close();
        }
    }

    @Test
    public void fixedItfAreaWriterStillRejectsOverlapAboveTolerance() throws Exception {
        ObjectPoolManager objectPoolManager = new ObjectPoolManager();
        try {
            double tooSmallTolerance = 1E-9;
            ItfAreaPolygon2LinetableFixed builder = new ItfAreaPolygon2LinetableFixed(
                    objectPoolManager, tooSmallTolerance);
            builder.addLines("1", null, createProblemLines());

            try {
                builder.getLines();
                fail("Expected intersection because overlap exceeds configured tolerance");
            } catch (IoxException e) {
                assertEquals("intersections", e.getMessage());
                System.out.println("FIXED: still rejected linework when maxOverlap=" + tooSmallTolerance);
            }
        } finally {
            objectPoolManager.close();
        }
    }

    private static ArrayList<IomObject> createProblemLines() {
        ArrayList<IomObject> lines = new ArrayList<IomObject>();
        lines.add(createArcPolyline());
        lines.add(createStraightPolyline());
        return lines;
    }

    private static IomObject createArcPolyline() {
        IomObject polyline = newPolyline();
        addCoord(polyline, ARC_START.x, ARC_START.y);
        addArc(polyline, ARC_POINT.x, ARC_POINT.y, COMMON_POINT.x, COMMON_POINT.y);
        return polyline;
    }

    private static IomObject createStraightPolyline() {
        IomObject polyline = newPolyline();
        addCoord(polyline, COMMON_POINT.x, COMMON_POINT.y);
        addCoord(polyline, STRAIGHT_END.x, STRAIGHT_END.y);
        return polyline;
    }

    private static IomObject newPolyline() {
        IomObject polyline = new Iom_jObject("POLYLINE", null);
        IomObject sequence = new Iom_jObject("SEGMENTS", null);
        polyline.addattrobj("sequence", sequence);
        return polyline;
    }

    private static void addCoord(IomObject polyline, double x, double y) {
        IomObject sequence = polyline.getattrobj("sequence", 0);
        IomObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(x));
        coord.setattrvalue("C2", Double.toString(y));
        sequence.addattrobj("segment", coord);
    }

    private static void addArc(IomObject polyline, double arcX, double arcY, double endX, double endY) {
        IomObject sequence = polyline.getattrobj("sequence", 0);
        IomObject arc = new Iom_jObject("ARC", null);
        arc.setattrvalue("A1", Double.toString(arcX));
        arc.setattrvalue("A2", Double.toString(arcY));
        arc.setattrvalue("C1", Double.toString(endX));
        arc.setattrvalue("C2", Double.toString(endY));
        sequence.addattrobj("segment", arc);
    }
}
