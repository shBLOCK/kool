package de.fabmax.kool.demo

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.randomF
import de.fabmax.kool.math.toRad
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.shading.Albedo
import de.fabmax.kool.pipeline.shading.ModeledShader
import de.fabmax.kool.pipeline.shading.PbrShader
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.ui.*
import de.fabmax.kool.util.*
import kotlin.math.*

fun multiLightDemo(ctx: KoolContext): List<Scene> {
    return MultiLightDemo(ctx).scenes
}

class MultiLightDemo(ctx: KoolContext) {
    val scenes = mutableListOf<Scene>()

    private val mainScene: Scene
    private val lights = listOf(
            LightMesh(Color.MD_CYAN),
            LightMesh(Color.MD_RED),
            LightMesh(Color.MD_AMBER),
            LightMesh(Color.MD_GREEN))
    private val depthPasses = mutableListOf<ShadowMapPass>()

    private var lightCount = 4
    private var lightPower = 500f
    private var lightSaturation = 0.4f
    private var lightRandomness = 0.3f
    private var autoRotate = true
    private var showLightIndicators = true

    private val colorCycler = Cycler(matColors).apply { index = 1 }

    private var modelShader: PbrShader? = null

    init {
        mainScene = mainScene(ctx)
        scenes += mainScene
        scenes += menu(ctx)

        lights.forEach {
            val pass = ShadowMapPass(mainScene, it.light)
            depthPasses += pass
            ctx.offscreenPasses += pass.offscreenPass
        }
    }

    private fun mainScene(ctx: KoolContext) = scene {
        +orbitInputTransform {
            +camera
            zoomMethod = OrbitInputTransform.ZoomMethod.ZOOM_CENTER
            zoom = 17f
            translation.set(0f, 2f, 0f)
            setMouseRotation(0f, -20f)
            // let the camera slowly rotate around vertical axis
            onPreRender += { ctx ->
                if (autoRotate) {
                    verticalRotation += ctx.deltaT * 3f
                }
            }
        }

        lighting.lights.clear()
        lights.forEach { +it }
        updateLighting()

        ctx.loadModel("bunny.kmfz", 0.05f, Vec3f(0f, 3.75f, 0f)) {
            +it

            val cfg = PbrShader.PbrConfig().apply {
                albedoSource = Albedo.STATIC_ALBEDO
                isReceivingShadows = true
            }
            modelShader = PbrShader(cfg).apply {
                albedo = colorCycler.current.linColor
                roughness = 0.1f
                metallic = 0f

                onCreated += {
                    depthMaps?.let { maps ->
                        depthPasses.forEachIndexed { i, pass ->
                            if (i < maps.size) {
                                maps[i] = pass.offscreenPass.impl.depthTexture
                            }
                        }
                    }
                }
            }
            it.pipelineLoader = modelShader
        }

        +textureMesh(isNormalMapped = true) {
            // ground doesn't need to cast shadows (their's nothing underneath it...)
            isCastingShadow = false

            generate {
                rect {
                    rotate(-90f, Vec3f.X_AXIS)
                    size.set(100f, 100f)
                    origin.set(-size.x / 2, -size.y / 2, 0f)
                    fullTexCoords(4f)
                }
            }

            val cfg = PbrShader.PbrConfig().apply {
                albedoSource = Albedo.TEXTURE_ALBEDO
                isNormalMapped = true
                isRoughnessMapped = true
                isReceivingShadows = true
            }
            pipelineLoader = PbrShader(cfg).apply {
                val basePath = Demo.getProperty("pbrDemo.materials", "https://fabmax-kool-pbr.s3.eu-central-1.amazonaws.com/materials")
                albedoMap = Texture { it.loadTextureData("$basePath/woodfloor/WoodFlooringMahoganyAfricanSanded001_COL_2K.jpg") }
                normalMap = Texture { it.loadTextureData("$basePath/woodfloor/WoodFlooringMahoganyAfricanSanded001_NRM_2K.jpg") }
                roughnessMap = Texture { it.loadTextureData("$basePath/woodfloor/WoodFlooringMahoganyAfricanSanded001_REFL_2K.jpg") }
                metallic = 0f

                onCreated += {
                    depthMaps?.let { maps ->
                        depthPasses.forEachIndexed { i, pass ->
                            if (i < maps.size) {
                                maps[i] = pass.offscreenPass.impl.depthTexture
                            }
                        }
                    }
                }
            }
        }
    }

    private fun menu(ctx: KoolContext) = uiScene {
        val smallFontProps = FontProps(Font.SYSTEM_FONT, 14f)
        val smallFont = uiFont(smallFontProps.family, smallFontProps.sizePts, uiDpi, ctx, smallFontProps.style, smallFontProps.chars)
        theme = theme(UiTheme.DARK) {
            componentUi { BlankComponentUi() }
            containerUi { BlankComponentUi() }
        }

        +container("menu container") {
            ui.setCustom(SimpleComponentUi(this))
            layoutSpec.setOrigin(dps(-450f), dps(-535f), zero())
            layoutSpec.setSize(dps(330f), dps(415f), full())

            // light setup
            var y = -40f
            +label("lights") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(30f), full())
                font.setCustom(smallFont)
                text = "Lights"
                textColor.setCustom(theme.accentColor)
                textAlignment = Gravity(Alignment.CENTER, Alignment.CENTER)
            }

            // light count
            y -= 35f
            +label("lightCntLbl") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(25f), dps(35f), full())
                text = "Lights:"
            }
            val btnLightCnt = button("lightCnt") {
                layoutSpec.setOrigin(pcs(45f), dps(y), zero())
                layoutSpec.setSize(pcs(40f), dps(35f), full())
                text = "$lightCount"

                onClick += { _, _, _ ->
                    lightCount++
                    if (lightCount > 4) { lightCount = 1 }
                    text = "$lightCount"
                    updateLighting()
                }
            }
            +btnLightCnt
            +button("decLightCnt") {
                layoutSpec.setOrigin(pcs(35f), dps(y), zero())
                layoutSpec.setSize(pcs(10f), dps(35f), full())
                text = "<"

                onClick += { _, _, _ ->
                    lightCount--
                    if (lightCount < 1) { lightCount = 4 }
                    btnLightCnt.text = "$lightCount"
                    updateLighting()
                }
            }
            +button("incLightCnt") {
                layoutSpec.setOrigin(pcs(85f), dps(y), zero())
                layoutSpec.setSize(pcs(10f), dps(35f), full())
                text = ">"

                onClick += { _, _, _ ->
                    lightCount++
                    if (lightCount > 4) { lightCount = 1 }
                    btnLightCnt.text = "$lightCount"
                    updateLighting()
                }
            }

            // light strength / brightness
            y -= 35f
            +label("lightPowerLbl") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(25f), dps(35f), full())
                text = "Strength:"
            }
            +slider("lightPowerSlider") {
                layoutSpec.setOrigin(pcs(30f), dps(y), zero())
                layoutSpec.setSize(pcs(70f), dps(35f), full())
                value = lightPower / 10f

                onValueChanged += {
                    lightPower = value * 10f
                    updateLighting()
                }
            }

            y -= 35f
            +label("saturationLbl") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(35f), full())
                text = "Saturation:"
            }
            +slider("saturationSlider") {
                layoutSpec.setOrigin(pcs(30f), dps(y), zero())
                layoutSpec.setSize(pcs(70f), dps(35f), full())
                value = lightSaturation * 100f

                onValueChanged += {
                    lightSaturation = value / 100f
                    updateLighting()
                }
            }
            y -= 35f
            +label("randomLbl") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(35f), full())
                text = "Random:"
            }
            +slider("randomSlider") {
                layoutSpec.setOrigin(pcs(30f), dps(y), zero())
                layoutSpec.setSize(pcs(70f), dps(35f), full())
                value = lightRandomness * 100f

                onValueChanged += {
                    lightRandomness = value / 100f
                }
            }

            y -= 40f
            +label("material") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(30f), full())
                font.setCustom(smallFont)
                text = "Material"
                textColor.setCustom(theme.accentColor)
                textAlignment = Gravity(Alignment.CENTER, Alignment.CENTER)
            }
            y -= 35f
            +label("colorLbl") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(25f), dps(35f), full())
                text = "Color:"
            }
            val matLabel = button("selected-color") {
                layoutSpec.setOrigin(pcs(45f), dps(y), zero())
                layoutSpec.setSize(pcs(40f), dps(35f), full())
                text = colorCycler.current.name

                onClick += { _, _, _ ->
                    text = colorCycler.next().name
                    modelShader?.albedo = colorCycler.current.linColor
                }
            }
            +matLabel
            +button("color-left") {
                layoutSpec.setOrigin(pcs(35f), dps(y), zero())
                layoutSpec.setSize(pcs(10f), dps(35f), full())
                text = "<"

                onClick += { _, _, _ ->
                    matLabel.text = colorCycler.prev().name
                    modelShader?.albedo = colorCycler.current.linColor
                }
            }
            +button("color-right") {
                layoutSpec.setOrigin(pcs(85f), dps(y), zero())
                layoutSpec.setSize(pcs(10f), dps(35f), full())
                text = ">"

                onClick += { _, _, _ ->
                    matLabel.text = colorCycler.next().name
                    modelShader?.albedo = colorCycler.current.linColor
                }
            }
            y -= 35f
            +label("roughnessLbl") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(25f), dps(35f), full())
                text = "Roughness:"
            }
            +slider("roughhnessSlider") {
                layoutSpec.setOrigin(pcs(30f), dps(y), zero())
                layoutSpec.setSize(pcs(70f), dps(35f), full())
                value = 10f

                onValueChanged += {
                    modelShader?.roughness = value / 100f
                }
            }

            y -= 40f
            +label("scene") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(30f), full())
                font.setCustom(smallFont)
                text = "Scene"
                textColor.setCustom(theme.accentColor)
                textAlignment = Gravity(Alignment.CENTER, Alignment.CENTER)
            }
            y -= 35f
            +toggleButton("tbAutoRot") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(30f), full())
                text = "Auto Rotate"
                isEnabled = autoRotate
                onStateChange += {
                    autoRotate = isEnabled
                }
            }
            y -= 35f
            +toggleButton("tbLightIndicators") {
                layoutSpec.setOrigin(pcs(0f), dps(y), zero())
                layoutSpec.setSize(pcs(100f), dps(30f), full())
                text = "Light Indicators"
                isEnabled = showLightIndicators
                onStateChange += {
                    showLightIndicators = isEnabled
                    updateLighting()
                }
            }
        }
    }

    private fun updateLighting() {
        lights.forEach { it.disable() }

        var pos = 0f
        val step = 360f / lightCount
        for (i in 0 until min(lightCount, lights.size)) {
            lights[i].setup(pos)
            lights[i].enable()
            pos += step
        }

        lights.forEach { it.updateVisibility() }
    }

    private inner class LightMesh(val color: Color) : TransformGroup() {
        val light = Light()

        private val spotAngleMesh = LineMesh().apply { isCastingShadow = false }

        private var isEnabled = true
        private var animPos = 0.0
        private val lightMeshShader = ModeledShader.StaticColor()
        private val meshPos = MutableVec3f()
        private var anglePos = 0f
        private val rotOff = randomF(0f, 3f)

        init {
            light.setSpot(Vec3f.ZERO, Vec3f.X_AXIS, 50f)
            val lightMesh = colorMesh {
                isCastingShadow = false
                generate {
                    sphere {
                        radius = 0.1f
                    }
                    rotate(90f, Vec3f.Z_AXIS)
                    cylinder {
                        bottomRadius = 0.015f
                        topRadius = 0.015f
                        height = 0.85f
                        steps = 4
                    }
                    translate(0f, 0.85f, 0f)
                    cylinder {
                        bottomRadius = 0.1f
                        topRadius = 0.0025f
                        height = 0.15f
                    }
                }
                pipelineLoader = lightMeshShader
            }
            +lightMesh
            +spotAngleMesh

            onPreRender += { ctx ->
                if (autoRotate) {
                    animPos += ctx.deltaT
                }

                val r = cos(animPos / 15 + rotOff).toFloat() * lightRandomness
                light.spotAngle = 60f - r * 20f
                updateSpotAngleMesh()

                setIdentity()
                rotate(animPos.toFloat() * -10f, Vec3f.Y_AXIS)
                translate(meshPos)
                rotate(anglePos, Vec3f.Y_AXIS)
                rotate(30f + 20f * r, Vec3f.Z_AXIS)

                transform.transform(light.position.set(Vec3f.ZERO), 1f)
                transform.transform(light.direction.set(Vec3f.NEG_X_AXIS), 0f)
            }
        }

        private fun updateSpotAngleMesh() {
            val r = 1f * tan(light.spotAngle.toRad() / 2)
            val c = lightMeshShader.color
            val n = 40

            spotAngleMesh.clear()
            for (i in 0 until n) {
                val a0 = i.toFloat() / n * 2 * PI
                val a1 = (i+1).toFloat() / n * 2 * PI
                spotAngleMesh.addLine(Vec3f(-1f, cos(a0).toFloat() * r, sin(a0).toFloat() * r), c,
                        Vec3f(-1f, cos(a1).toFloat() * r, sin(a1).toFloat() * r), c)
            }
            val e = cos(45f.toRad()) * r
            spotAngleMesh.addLine(Vec3f.ZERO, c, Vec3f(-1f, e, e), c)
            spotAngleMesh.addLine(Vec3f.ZERO, c, Vec3f(-1f, e, -e), c)
            spotAngleMesh.addLine(Vec3f.ZERO, c, Vec3f(-1f, -e, -e), c)
            spotAngleMesh.addLine(Vec3f.ZERO, c, Vec3f(-1f, -e, e), c)
        }

        fun setup(angPos: Float) {
            val x = cos(angPos.toRad()) * 10f
            val z = sin(angPos.toRad()) * 10f
            meshPos.set(x, 9f, -z)
            anglePos = angPos
            val color = Color.WHITE.mix(color, lightSaturation, MutableColor())
            light.setColor(color.toLinear(), lightPower)
            lightMeshShader.color = color
            updateSpotAngleMesh()
        }

        fun enable() {
            isEnabled = true
            scene?.lighting?.lights?.apply {
                if (!contains(light)) {
                    add(light)
                }
            }
            updateVisibility()
        }

        fun disable() {
            isEnabled = false
            scene?.lighting?.lights?.remove(light)
            updateVisibility()
        }

        fun updateVisibility() {
            isVisible = isEnabled && showLightIndicators
        }
    }

    private fun KoolContext.loadModel(path: String, scale: Float, translation: Vec3f, recv: (Mesh) -> Unit) {
        assetMgr.loadModel(path) { model ->
            if (model != null) {
                val mesh = model.meshes[0].toMesh()
                mesh.geometry.forEach {
                    it.position.scale(scale).add(translation)
                }
                recv(mesh)
            }
        }
    }

    private data class MatColor(val name: String, val linColor: Color)

    companion object {
        private val matColors = listOf(
                MatColor("White", Color.WHITE.toLinear()),
                MatColor("Red", Color.MD_RED.toLinear()),
                MatColor("Pink", Color.MD_PINK.toLinear()),
                MatColor("Purple", Color.MD_PURPLE.toLinear()),
                MatColor("Deep Purple", Color.MD_DEEP_PURPLE.toLinear()),
                MatColor("Indigo", Color.MD_INDIGO.toLinear()),
                MatColor("Blue", Color.MD_BLUE.toLinear()),
                MatColor("Cyan", Color.MD_CYAN.toLinear()),
                MatColor("Teal", Color.MD_TEAL.toLinear()),
                MatColor("Green", Color.MD_GREEN.toLinear()),
                MatColor("Light Green", Color.MD_LIGHT_GREEN.toLinear()),
                MatColor("Lime", Color.MD_LIME.toLinear()),
                MatColor("Yellow", Color.MD_YELLOW.toLinear()),
                MatColor("Amber", Color.MD_AMBER.toLinear()),
                MatColor("Orange", Color.MD_ORANGE.toLinear()),
                MatColor("Deep Orange", Color.MD_DEEP_ORANGE.toLinear()),
                MatColor("Brown", Color.MD_BROWN.toLinear()),
                MatColor("Grey", Color.MD_GREY.toLinear()),
                MatColor("Blue Grey", Color.MD_BLUE_GREY.toLinear()),
                MatColor("Almost Black", Color(0.1f, 0.1f, 0.1f).toLinear())
        )
    }
}