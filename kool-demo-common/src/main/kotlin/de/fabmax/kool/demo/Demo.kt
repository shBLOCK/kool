package de.fabmax.kool.demo

import de.fabmax.kool.KoolContext
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.ui.*
import de.fabmax.kool.util.debugOverlay

/**
 * @author fabmax
 */

class Demo(ctx: KoolContext, startScene: String? = null) {

    private val dbgOverlay = debugOverlay(ctx, true)
    private val newScenes = mutableListOf<Scene>()
    private val currentScenes = mutableListOf<Scene>()

    private val defaultScene = DemoEntry("Simple Demo") { add(simpleShapesScene(it)) }
    private val demos = mutableMapOf(
            "simpleDemo" to defaultScene,
            "multiDemo" to DemoEntry("Split Viewport Demo") { addAll(multiScene(it)) },
            "pointDemo" to DemoEntry("Point Cloud Demo") { add(pointScene()) },
            "synthieDemo" to DemoEntry("Synthie Demo") { addAll(synthieScene(it)) },
            "globeDemo" to DemoEntry("Globe Demo") { addAll(globeScene(it)) },
            "modelDemo" to DemoEntry("Model Demo") { add(modelScene(it)) },
            "treeDemo" to DemoEntry("Tree Demo") { addAll(treeScene(it)) },
            "boxDemo" to DemoEntry("Physics Demo") { addAll(collisionDemo(it)) }
    )

    init {
        ctx.scenes += demoOverlay(ctx)
        ctx.scenes += dbgOverlay
        ctx.onRender += this::onRender

        //dbgOverlay.isVisible = false

        (demos[startScene] ?: defaultScene).loadScene(newScenes, ctx)

        ctx.run()
    }

    private fun onRender(ctx: KoolContext) {
        if (!newScenes.isEmpty()) {
            currentScenes.forEach { s ->
                ctx.scenes -= s
                s.dispose(ctx)
            }
            currentScenes.clear()

            // new scenes have to be inserted in front, so that demo menu is rendered after it
            newScenes.forEachIndexed { i, s ->
                ctx.scenes.add(i, s)
                currentScenes.add(s)
            }
            newScenes.clear()
        }
    }

    private fun demoOverlay(ctx: KoolContext): Scene = uiScene(ctx.screenDpi) {
        theme = theme(UiTheme.DARK) {
            componentUi { BlankComponentUi() }
            containerUi(::BlurredComponentUi)
        }
        content.ui.setCustom(BlankComponentUi())

        +drawerMenu("menu", "Demos") {
            // no nice layouting functions yet, choose start y such that menu items start somewhere below the title
            // negative value means it's measured from top
            var y = -105f
            for (demo in demos) {
                +button(demo.key) {
                    layoutSpec.setOrigin(zero(), dps(y, true), zero())
                    layoutSpec.setSize(pcs(100f, true), dps(30f, true), zero())
                    textAlignment = Gravity(Alignment.START, Alignment.CENTER)
                    text = demo.value.label
                    y -= 35f

                    onClick += { _,_,_ ->
                        demo.value.loadScene.invoke(newScenes, ctx)
                        isOpen = false
                    }
                }
            }

            +toggleButton("showDbg") {
                layoutSpec.setOrigin(zero(), dps(10f, true), zero())
                layoutSpec.setSize(pcs(100f, true), dps(30f, true), zero())
                text = "Debug Info"
                isEnabled = dbgOverlay.isVisible

                onClick += { _,_,_ -> dbgOverlay.isVisible = isEnabled }
            }
        }
    }

    private class DemoEntry(val label: String, val loadScene: MutableList<Scene>.(KoolContext) -> Unit)
}