package ch.interlis.reproducer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import ch.ehi.iox.objpool.ObjectPoolManager;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.itf.impl.ItfAreaPolygon2Linetable;
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
        assertTrue("overlap should be below DMAV tolerance, but was " + intersector.getOverlap(),
                intersector.getOverlap() < DMAV_MAX_OVERLAP);

        System.out.println("intersection 1: " + intersector.getIntersection(0));
        System.out.println("intersection 2: " + intersector.getIntersection(1));
        System.out.println("INTERLIS overlap: " + intersector.getOverlap());
        System.out.println("DMAV max overlap: " + DMAV_MAX_OVERLAP);
    }

    @Test
    public void itfAreaWriterRejectsToleranceValidLinework() throws Exception {
        ObjectPoolManager objectPoolManager = new ObjectPoolManager();
        try {
            ItfAreaPolygon2Linetable builder = new ItfAreaPolygon2Linetable("Reproducer.Topic.Area", objectPoolManager);

            ArrayList<IomObject> lines = new ArrayList<IomObject>();
            lines.add(createArcPolyline());
            lines.add(createStraightPolyline());
            builder.addLines("1", null, lines);

            try {
                builder.getLines();
                fail("Expected current iox-ili behavior: IoxException: intersections");
            } catch (IoxException e) {
                assertEquals("intersections", e.getMessage());
                System.out.println("Reproduced current ITF AREA writer failure: " + e.getMessage());
            }
        } finally {
            objectPoolManager.close();
        }
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
