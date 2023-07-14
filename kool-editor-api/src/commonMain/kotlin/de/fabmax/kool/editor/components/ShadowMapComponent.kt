package de.fabmax.kool.editor.components

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.editor.api.AppState
import de.fabmax.kool.editor.data.ShadowMapComponentData
import de.fabmax.kool.editor.data.ShadowMapInfo
import de.fabmax.kool.editor.data.ShadowMapTypeData
import de.fabmax.kool.editor.model.EditorNodeModel
import de.fabmax.kool.editor.model.SceneModel
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.scene.Light
import de.fabmax.kool.util.*

class ShadowMapComponent(override val componentData: ShadowMapComponentData) :
    SceneNodeComponent(),
    EditorDataComponent<ShadowMapComponentData>
{
    val shadowMapState = mutableStateOf(componentData.shadowMap).onChange {
        if (AppState.isEditMode) { componentData.shadowMap = it }
        updateShadowMap(it, componentData.clipNear, componentData.clipFar)
    }

    val clipNear = mutableStateOf(componentData.clipNear).onChange {
        if (AppState.isEditMode) { componentData.clipNear = it }
        updateShadowMap(componentData.shadowMap, it, componentData.clipFar)
    }

    val clipFar = mutableStateOf(componentData.clipNear).onChange {
        if (AppState.isEditMode) { componentData.clipFar = it }
        updateShadowMap(componentData.shadowMap, componentData.clipNear, it)
    }

    private var shadowMap: ShadowMap? = null

    constructor() : this(
        ShadowMapComponentData(
            ShadowMapTypeData.Single(ShadowMapInfo())
        )
    )

    init {
        dependsOn(DiscreteLightComponent::class)
    }

    override suspend fun createComponent(nodeModel: EditorNodeModel) {
        super.createComponent(nodeModel)
        shadowMapState.set(componentData.shadowMap)
    }

    override fun onNodeRemoved(nodeModel: EditorNodeModel) {
        disposeShadowMap()
        UpdateShadowMapsComponent.updateShadowMaps(sceneModel)
    }

    override fun onNodeAdded(nodeModel: EditorNodeModel) {
        updateShadowMap()
    }

    fun updateLight(light: Light) {
        val current = shadowMap?.light
        if (current != null && current::class == light::class) {
            shadowMap?.light = light
        } else {
            updateShadowMap()
        }
    }

    private fun updateShadowMap() {
        if (isCreated) {
            updateShadowMap(componentData.shadowMap, componentData.clipNear, componentData.clipFar)
        }
    }

    private fun updateShadowMap(shadowMapInfo: ShadowMapTypeData, clipNear: Float, clipFar: Float) {
        logD { "Update shadow map: $shadowMapInfo, near: $clipNear, far: $clipFar" }

        val light = nodeModel.getComponent<DiscreteLightComponent>()?.light
        if (light == null) {
            logE { "Unable to get DiscreteLightComponent of sceneNode ${nodeModel.name}" }
            return
        }
        if (light is Light.Point) {
            logE { "Point light shadow maps are not yet supported" }
            return
        }

        val scene = sceneModel.drawNode

        // dispose old shadow map
        disposeShadowMap()

        // create new shadow map
        shadowMap = when (shadowMapInfo) {
            is ShadowMapTypeData.Single -> {
                SimpleShadowMap(scene, light, shadowMapInfo.mapInfo.mapSize).apply {
                    this.clipNear = clipNear
                    this.clipFar = clipFar
                }
            }
            is ShadowMapTypeData.Cascaded -> {
                CascadedShadowMap(
                    scene,
                    light,
                    clipFar,
                    shadowMapInfo.mapInfos.size,
                    mapSizes = shadowMapInfo.mapInfos.map { it.mapSize }
                ).apply {
                    shadowMapInfo.mapInfos.forEachIndexed { i, info ->
                        mapRanges[i].near = info.rangeNear
                        mapRanges[i].far = info.rangeFar
                    }
                }
            }
        }.also {
            sceneModel.shaderData.shadowMaps += it
            UpdateShadowMapsComponent.updateShadowMaps(sceneModel)
        }
    }

    private fun disposeShadowMap() {
        val scene = sceneModel.drawNode
        shadowMap?.let {
            sceneModel.shaderData.shadowMaps -= it
            val ctx = KoolSystem.requireContext()
            when (it) {
                is SimpleShadowMap -> {
                    scene.removeOffscreenPass(it)
                    it.dispose(ctx)
                }
                is CascadedShadowMap -> {
                    it.subMaps.forEach { pass ->
                        scene.removeOffscreenPass(pass)
                        pass.dispose(ctx)
                    }
                }
            }
        }
    }
}

interface UpdateShadowMapsComponent {
    fun updateShadowMaps(shadowMaps: List<ShadowMap>)

    companion object {
        fun updateShadowMaps(sceneModel: SceneModel) {
            sceneModel.project.getComponentsInScene<UpdateShadowMapsComponent>(sceneModel).forEach {
                it.updateShadowMaps(sceneModel.shaderData.shadowMaps)
            }
        }
    }
}