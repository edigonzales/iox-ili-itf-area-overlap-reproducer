# iox-ili ITF AREA overlap reproducer

Minimal reproducer for [claeis/ili2db#597](https://github.com/claeis/ili2db/issues/597).

The example uses the exact arc/straight combination reported by ili2db while exporting `DM01AVCH24LV95D.Bodenbedeckung.BoFlaeche_Geometrie` to INTERLIS 1:

```text
arc:
  start  (2754998.564, 1260356.465)
  arc    (2754997.839, 1260355.844)
  end    (2754997.727, 1260354.897)

straight:
  start  (2754997.727, 1260354.897)
  end    (2754996.744, 1260358.680)
```

The two segments share the point `(2754997.727, 1260354.897)`. `CurveSegmentIntersector` nevertheless finds two intersections and reports an INTERLIS overlap of about `3.8944E-8`. This is far below the DMAV `WITHOUT OVERLAPS > 0.002` tolerance (and also below the DM.01 tolerance of `0.050`).

The reproducer passes the same two polylines to both implementations in the same JVM:

- the unmodified `ItfAreaPolygon2Linetable` from `iox-ili:1.24.4`;
- `ItfAreaPolygon2LinetableFixed`, a reduced copy of the same code path with the proposed tolerance-aware change.

## Run

Requires Java and Gradle:

```bash
gradle test
```

## Expected result

The test suite is deliberately **green** while showing the difference between current and proposed behavior:

- `arcStraightOverlapIsWithinDmavTolerance` freezes the exact intersection coordinates and overlap from issue #597.
- `upstreamItfAreaWriterRejectsToleranceValidLinework` shows that the current writer throws `IoxException: intersections`.
- `fixedItfAreaWriterAcceptsToleranceValidLinework` shows that the candidate fix accepts exactly the same geometry with `maxOverlap=0.002`.
- `fixedItfAreaWriterStillRejectsOverlapAboveTolerance` runs the same geometry with `maxOverlap=1E-9` and proves that the candidate fix still rejects it when the configured tolerance is actually exceeded.

## Candidate fix

The current `ItfAreaPolygon2Linetable.getLines()` treats every intersection reported by `CompoundCurveNoder` as fatal. The proposed change filters the intersections with the same basic rule already used by the regular `AreaValidator`:

1. a real segment overlay remains invalid;
2. a single intersection is valid only at a shared segment control point;
3. with two intersections, if both are shared control points the situation is valid;
4. if exactly one is a shared control point, the second intersection is valid only when `intersection.getOverlap() <= maxOverlap`;
5. crossings without a shared control point remain invalid.

This is intentionally narrower than simply ignoring every intersection whose overlap happens to be small.

For a real upstream change, `ItfAreaPolygon2Linetable` would need to receive the AREA type's `maxOverlap`. `ItfWriter2` already has the `AttributeDef attr`, so it can obtain it from the resolved `AreaType`, roughly:

```java
AreaType areaType = (AreaType) attr.getDomainResolvingAliases();
PrecisionDecimal maxOverlapValue = areaType.getMaxOverlap();
double maxOverlap = maxOverlapValue != null ? maxOverlapValue.doubleValue() : 0.0;

ItfAreaPolygon2Linetable allLines =
        new ItfAreaPolygon2Linetable(tableQName, recman, maxOverlap);
```

The existing two-argument constructor could remain and delegate with `maxOverlap=0.0` for compatibility.

## Why this is interesting

The regular AREA validation path already has a `maxOverlap` concept. The INTERLIS 1 line-table construction in `ItfAreaPolygon2Linetable.getLines()` currently uses `CompoundCurveNoder` without applying that model tolerance. The reproducer isolates this difference from ili2db, PostGIS, DMAV-to-DM.01 transformation SQL, and coordinate rounding.
