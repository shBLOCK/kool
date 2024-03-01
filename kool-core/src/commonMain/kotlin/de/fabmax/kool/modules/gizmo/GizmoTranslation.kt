package de.fabmax.kool.modules.gizmo

import de.fabmax.kool.math.MutableVec3d
import de.fabmax.kool.math.PlaneD
import de.fabmax.kool.math.RayD
import de.fabmax.kool.math.Vec3d

class AxisTranslation(val axis: GizmoHandle.Axis) : GizmoOperationBase() {
    private val dragAxis = RayD(Vec3d.ZERO, axis.axis)
    private var dragDistanceOffset = 0.0

    override fun onDragStart(dragCtx: DragContext) {
        dragDistanceOffset = dragAxis.signedDistance(dragCtx.localRay) ?: return
        dragCtx.startManipulation()
    }

    override fun onDrag(dragCtx: DragContext) {
        val dist = dragAxis.signedDistance(dragCtx.localRay) ?: return
        dragCtx.manipulateAxisTranslation(axis, dist - dragDistanceOffset)
    }
}

class PlaneTranslation(planeNormal: Vec3d) : GizmoOperationBase() {
    private val dragPlane = PlaneD(Vec3d.ZERO, planeNormal)
    private val dragPointerOffset = MutableVec3d()

    override fun onDragStart(dragCtx: DragContext) {
        if (dragPlane.intersection(dragCtx.localRay, dragPointerOffset)) {
            dragCtx.startManipulation()
        }
    }

    override fun onDrag(dragCtx: DragContext) {
        val point = MutableVec3d()
        if (dragPlane.intersection(dragCtx.localRay, point)) {
            dragCtx.manipulateTranslation(point - dragPlane.p - dragPointerOffset)
        }
    }
}

class CamPlaneTranslation : GizmoOperationBase() {
    private val dragPlane = PlaneD()
    private val dragPointerOffset = MutableVec3d()

    override fun onDragStart(dragCtx: DragContext) {
        dragCtx.globalToLocal.transform(dragCtx.camera.dataD.globalLookDir, 0.0, dragPlane.n)
        dragPlane.n.norm()
        dragPlane.intersection(dragCtx.localRay, dragPointerOffset)
        dragCtx.startManipulation()
    }

    override fun onDrag(dragCtx: DragContext) {
        val point = MutableVec3d()
        if (dragPlane.intersection(dragCtx.localRay, point)) {
            dragCtx.manipulateTranslation(point - dragPlane.p - dragPointerOffset)
        }
    }
}