package ch.interlis.iom_j.itf.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.iox.objpool.ObjectPoolManager;
import ch.ehi.iox.objpool.impl.CompoundCurveSerializer;
import ch.ehi.iox.objpool.impl.FileBasedCollection;
import ch.ehi.iox.objpool.impl.IomObjectSerializer;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.itf.impl.jtsext.geom.CompoundCurve;
import ch.interlis.iom_j.itf.impl.jtsext.geom.CurveSegment;
import ch.interlis.iom_j.itf.impl.jtsext.noding.CompoundCurveDissolver;
import ch.interlis.iom_j.itf.impl.jtsext.noding.CompoundCurveNoder;
import ch.interlis.iom_j.itf.impl.jtsext.noding.Intersection;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.jts.Iox2jtsext;
import ch.interlis.iox_j.jts.Jtsext2iox;

/**
 * Reduced copy of {@link ItfAreaPolygon2Linetable} containing the proposed fix.
 *
 * <p>The original writer treats every intersection reported by
 * {@link CompoundCurveNoder} as fatal. This variant applies the same basic
 * tolerance semantics as the regular AREA validator: an arc-related second
 * intersection is accepted when one of the intersection points is a shared
 * segment control point and the reported INTERLIS overlap is within the
 * model's WITHOUT OVERLAPS tolerance.</p>
 *
 * <p>This class deliberately lives next to the upstream class under a different
 * name so the reproducer can exercise both implementations in the same JVM.</p>
 */
public class ItfAreaPolygon2LinetableFixed {
    private Collection<? extends CompoundCurve> lines;
    private Collection<IomObject> ioxlines;
    private final ObjectPoolManager recman;
    private final double maxOverlap;

    public ItfAreaPolygon2LinetableFixed(ObjectPoolManager recman, double maxOverlap) {
        this.recman = recman;
        this.maxOverlap = maxOverlap;
        lines = new FileBasedCollection<CompoundCurve>(
                recman,
                getClass().getSimpleName(),
                new CompoundCurveSerializer());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addLines(String mainObjTid, String internalTid, ArrayList<IomObject> ioxlines) throws IoxException {
        for (IomObject ioxline : ioxlines) {
            CompoundCurve line = Iox2jtsext.polyline2JTS(ioxline, false, 0.0);
            line.setUserData(internalTid != null ? internalTid : mainObjTid);
            ((Collection) lines).add(line);
        }
    }

    public List<IomObject> getLines() throws IoxException {
        if (ioxlines == null) {
            CompoundCurveNoder noder = new CompoundCurveNoder(recman, (List<?>) lines, false);
            noder.setEnableCommonSegments(true);

            List<Intersection> invalidIntersections = new ArrayList<Intersection>();
            for (Intersection intersection : noder.getIntersections()) {
                if (!isToleranceValidIntersection(intersection, maxOverlap)) {
                    invalidIntersections.add(intersection);
                }
            }

            if (!invalidIntersections.isEmpty()) {
                for (Intersection is : invalidIntersections) {
                    EhiLogger.logError("intersection tid1 " + is.getCurve1().getUserData()
                            + ", tid2 " + is.getCurve2().getUserData()
                            + ", coord " + is.getPt()[0].toString()
                            + (is.getPt().length == 2 ? (", coord2 " + is.getPt()[1].toString()) : ""));
                    EhiLogger.traceState("overlap " + is.getOverlap()
                            + ", seg1 " + is.getSegment1()
                            + ", seg2 " + is.getSegment2());
                }
                throw new IoxException("intersections");
            }

            lines = noder.getNodedSubstrings();

            CompoundCurveDissolver dissolver = new CompoundCurveDissolver();
            dissolver.dissolve(lines);
            lines = dissolver.getDissolved();

            ioxlines = new FileBasedCollection<IomObject>(
                    recman,
                    getClass().getSimpleName(),
                    new IomObjectSerializer());

            for (CompoundCurve line : lines) {
                try {
                    ioxlines.add(Jtsext2iox.JTS2polyline(line));
                } catch (Iox2jtsException e) {
                    throw new IoxException(e);
                }
            }
        }
        return (List<IomObject>) ioxlines;
    }

    /**
     * Inverse of the relevant part of AreaValidator.isInvalidProperIntersection().
     */
    static boolean isToleranceValidIntersection(Intersection intersection, double maxOverlap) {
        if (intersection.isOverlay()) {
            return false;
        }

        Coordinate[] points = intersection.getPt();
        CurveSegment segmentA = intersection.getSegment1();
        CurveSegment segmentB = intersection.getSegment2();

        if (points.length == 1) {
            return isControlPoint(points[0], segmentA) && isControlPoint(points[0], segmentB);
        }

        if (points.length == 2) {
            boolean p0AtSharedControlPoint = isControlPoint(points[0], segmentA)
                    && isControlPoint(points[0], segmentB);
            boolean p1AtSharedControlPoint = isControlPoint(points[1], segmentA)
                    && isControlPoint(points[1], segmentB);

            if (p0AtSharedControlPoint && p1AtSharedControlPoint) {
                return true;
            }
            if (!p0AtSharedControlPoint && !p1AtSharedControlPoint) {
                return false;
            }

            Double overlap = intersection.getOverlap();
            return overlap != null && overlap <= maxOverlap;
        }

        return false;
    }

    private static boolean isControlPoint(Coordinate point, CurveSegment segment) {
        return point.equals2D(segment.getStartPoint()) || point.equals2D(segment.getEndPoint());
    }
}
