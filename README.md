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

The reproducer then passes the same two polylines to `ItfAreaPolygon2Linetable`, which is used when an INTERLIS 1 `AREA` is converted to its helper line table. `getLines()` rejects the linework with:

```text
ch.interlis.iox.IoxException: intersections
```

## Run

Requires Java and Gradle:

```bash
gradle test
```

## Expected result with current iox-ili

The test suite is intentionally **red**:

- `arcStraightOverlapIsWithinDmavTolerance` passes and demonstrates that the reported overlap is below `0.002`.
- `itfAreaWriterShouldAcceptSameLinework` fails because `ItfAreaPolygon2Linetable.getLines()` throws `IoxException: intersections`.

After the underlying issue is fixed, both tests should pass without changing the geometry.

## Why this is interesting

The regular area validation path has a `maxOverlap` concept. The INTERLIS 1 line-table construction in `ItfAreaPolygon2Linetable.getLines()` uses `CompoundCurveNoder` and has no `maxOverlap` parameter. The reproducer isolates that difference from ili2db, PostGIS, DMAV-to-DM.01 transformation SQL, and coordinate rounding.
