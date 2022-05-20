package de.fabmax.kool.demo.physics.terrain

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.modules.ksl.KslLitShader
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.modules.ksl.blocks.*
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.GradientTexture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.Texture3d
import de.fabmax.kool.pipeline.ibl.EnvironmentMaps
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.ColorGradient
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.ShadowMap

object OceanShader {

    val oceanGradientTex = GradientTexture(ColorGradient(
        0.0f to MdColor.CYAN,
        0.1f to MdColor.LIGHT_BLUE,
        0.2f to MdColor.BLUE,
        0.5f to MdColor.INDIGO.mix(MdColor.BLUE, 0.5f),
        1.0f to (MdColor.INDIGO tone 800).mix(MdColor.BLUE tone 800, 0.5f),
        toLinear = true), isLinear = true)

    class Pbr(oceanFloor: OceanFloorRenderPass, ibl: EnvironmentMaps, shadowMap: ShadowMap, windTex: Texture3d, oceanBump: Texture2d)
        : KslPbrShader(pbrConfig(ibl, shadowMap)), WindAffectedShader {
        override var windOffsetStrength by uniform4f("uWindOffsetStrength")
        override var windScale by uniform1f("uWindScale", 0.01f)
        override var windDensity by texture3d("tWindTex", windTex)

        override val shader = this

        var oceanBump by texture2d("tOceanBump", oceanBump)
        var oceanFloorColor by texture2d("tOceanFloorColor", oceanFloor.colorTexture)
        var oceanFloorDepth by texture2d("tOceanFloorDepth", oceanFloor.depthTexture)
        var oceanGradient by texture1d("tOceanGradient", oceanGradientTex)
    }

    class BlinnPhong(oceanFloor: OceanFloorRenderPass, ibl: EnvironmentMaps, shadowMap: ShadowMap, windTex: Texture3d, oceanBump: Texture2d)
        : KslBlinnPhongShader(blinnPhongConfig(ibl, shadowMap)), WindAffectedShader {
        override var windOffsetStrength by uniform4f("uWindOffsetStrength")
        override var windScale by uniform1f("uWindScale", 0.01f)
        override var windDensity by texture3d("tWindTex", windTex)

        override val shader = this

        var oceanBump by texture2d("tOceanBump", oceanBump)
        var oceanFloorColor by texture2d("tOceanFloorColor", oceanFloor.colorTexture)
        var oceanFloorDepth by texture2d("tOceanFloorDepth", oceanFloor.depthTexture)
        var oceanGradient by texture1d("tOceanGradient", oceanGradientTex)
    }

    fun makeOceanShader(oceanFloor: OceanFloorRenderPass, ibl: EnvironmentMaps, shadowMap: ShadowMap, windTex: Texture3d, oceanBump: Texture2d, isPbr: Boolean): WindAffectedShader {
        return if (isPbr) {
            Pbr(oceanFloor, ibl, shadowMap, windTex, oceanBump)
        } else {
            BlinnPhong(oceanFloor, ibl, shadowMap, windTex, oceanBump)
        }
    }

    private fun KslLitShader.LitShaderConfig.baseConfig(shadowMap: ShadowMap) {
        vertices { isInstanced = true }
        color { constColor(MdColor.CYAN.toLinear()) }
        shadow { addShadowMap(shadowMap) }
        colorSpaceConversion = ColorSpaceConversion.LINEAR_TO_sRGB_HDR
        modelCustomizer = { oceanMod() }
    }

    private fun pbrConfig(ibl: EnvironmentMaps, shadowMap: ShadowMap) = KslPbrShader.Config().apply {
        baseConfig(shadowMap)
        roughness(0.1f)
        with (TerrainDemo) {
            iblConfig(ibl)
        }
    }

    private fun blinnPhongConfig(ibl: EnvironmentMaps, shadowMap: ShadowMap) = KslBlinnPhongShader.Config().apply {
        baseConfig(shadowMap)
        imageBasedAmbientColor(ibl.irradianceMap, Color.GRAY)
        specularStrength(1f)
        shininess(500f)
    }

    private fun KslProgram.oceanMod() {
        val windOffsetStrength = uniformFloat4("uWindOffsetStrength")
        val windScale = uniformFloat1("uWindScale")
        val windTex = texture3d("tWindTex")
        val oceanBumpTex = texture2d("tOceanBump")

        val posScreenSpace = interStageFloat4()
        val waveHeight = interStageFloat1()

        vertexStage {
            main {
                val worldPosPort = getFloat3Port("worldPos")

                val pos = float3Var(worldPosPort.input.input!!)
                val posLt = float3Var(pos + float3Value((-1f).const, 0f.const, 0f.const))
                val posRt = float3Var(pos + float3Value(1f.const, 0f.const, 0f.const))
                val posUp = float3Var(pos + float3Value(0f.const, 0f.const, (-1f).const))
                val posDn = float3Var(pos + float3Value(0f.const, 0f.const, 1f.const))

                val windScale2 = floatVar(windScale * 0.71.const)

                pos.y set (sampleTexture(windTex, (windOffsetStrength.xyz + pos) * windScale).y - 0.5f.const) * windOffsetStrength.w +
                        (sampleTexture(windTex, (windOffsetStrength.xyz + pos) * windScale2).y - 0.5f.const) * windOffsetStrength.w
                posLt.y set (sampleTexture(windTex, (windOffsetStrength.xyz + posLt) * windScale).y - 0.5f.const) * windOffsetStrength.w +
                        (sampleTexture(windTex, (windOffsetStrength.xyz + posLt) * windScale2).y - 0.5f.const) * windOffsetStrength.w
                posRt.y set (sampleTexture(windTex, (windOffsetStrength.xyz + posRt) * windScale).y - 0.5f.const) * windOffsetStrength.w +
                        (sampleTexture(windTex, (windOffsetStrength.xyz + posRt) * windScale2).y - 0.5f.const) * windOffsetStrength.w
                posUp.y set (sampleTexture(windTex, (windOffsetStrength.xyz + posUp) * windScale).y - 0.5f.const) * windOffsetStrength.w +
                        (sampleTexture(windTex, (windOffsetStrength.xyz + posUp) * windScale2).y - 0.5f.const) * windOffsetStrength.w
                posDn.y set (sampleTexture(windTex, (windOffsetStrength.xyz + posDn) * windScale).y - 0.5f.const) * windOffsetStrength.w +
                        (sampleTexture(windTex, (windOffsetStrength.xyz + posDn) * windScale2).y - 0.5f.const) * windOffsetStrength.w

                worldPosPort.input(pos)

                val normalA = float3Var(normalize(cross(posUp - pos, posLt - pos)))
                val normalB = float3Var(normalize(cross(posDn - pos, posRt - pos)))
                getFloat3Port("worldNormal").input(normalize(normalA + normalB))

                waveHeight.input set pos.y
                posScreenSpace.input set outPosition
            }
        }
        fragmentStage {
            main {
                val camData = cameraData()
                val material = findBlock<LitMaterialBlock>()!!
                val baseColorPort = getFloat4Port("baseColor")

//                val baseColor = float3Var()
//                val lightColor = MdColor.LIGHT_BLUE toneLin 300
//                val midColor = MdColor.LIGHT_BLUE toneLin 500
//                val darkColor = MdColor.BLUE toneLin 700
//                `if`(waveHeight.output gt 0f.const) {
//                    baseColor set mix(midColor.const.rgb, lightColor.const.rgb, clamp(waveHeight.output / 6f.const, 0f.const, 1f.const))
//                }.`else` {
//                    baseColor set mix(midColor.const.rgb, darkColor.const.rgb, clamp(-waveHeight.output / 6f.const, 0f.const, 1f.const))
//                }

                // sample bump map and compute fragment normal
                val worldPos = material.inFragmentPos.input!!
                val worldNormal = material.inNormal.input!!
                val tangent = float4Value(cross(worldNormal, Vec3f.X_AXIS.const), 1f.const)

                val offsetA = float2Var(windOffsetStrength.float2("xz") * 0.73f.const * windScale)
                val offsetB = float2Var(windOffsetStrength.float2("xz") * 0.37f.const * windScale)
                val offsetC = float2Var(windOffsetStrength.float2("xz") * 0.15f.const * windScale)

                val mapNormalA = sampleTexture(oceanBumpTex, worldPos.float2("xz") * 0.051f.const + offsetA).xyz * 2f.const - 1f.const
                val mapNormalB = (sampleTexture(oceanBumpTex, worldPos.float2("xz") * 0.017f.const + offsetB).xyz * 2f.const - 1f.const) * 0.7f.const
                val mapNormalC = (sampleTexture(oceanBumpTex, worldPos.float2("xz") * 0.005f.const + offsetC).xyz * 2f.const - 1f.const) * 0.3f.const
                val mapNormal = float3Var(normalize(mapNormalA + mapNormalB + mapNormalC))
                val bumpNormal = float3Var(calcBumpedNormal(worldNormal, tangent, mapNormal, 0.4f.const))
                material.inNormal(bumpNormal)

                // sample ocean floor and compute fragment color
                val camToFrag = float3Var(worldPos - camData.position)
                val fragDepth = floatVar(dot(camToFrag, camData.direction))
                camToFrag set normalize(camToFrag)

                // 1st depth sample - water depth without refraction
                val oceanFloorUv = posScreenSpace.output.float2("xy") / posScreenSpace.output.w * 0.5f.const + 0.5f.const
                val oceanDepth1 = floatVar(sampleTexture(texture2d("tOceanFloorDepth"), oceanFloorUv).x)
                oceanDepth1 set clamp(getLinearDepth(oceanDepth1, camData.clipNear, camData.clipFar) - fragDepth, 0f.const, 50f.const)

                // compute water refraction based on initial depth estimate and water surface normal
                //val refractPos = float3Var(worldPos + refract(normalize(camToFrag), bumpNormal, 1.33f.const) * oceanDepth)
                val refractPos = float3Var(worldPos + camToFrag * oceanDepth1 + bumpNormal * clamp(oceanDepth1 / 3f.const, 0f.const, 1f.const))
                val refractScreenSpace = float4Var(camData.viewProjMat * float4Value(refractPos, 1f))
                val refractUv = float2Var(refractScreenSpace.float2("xy") / refractScreenSpace.w * 0.5f.const + 0.5f.const)

                // 2nd depth sample - water depth at refracted position
                val oceanDepth2 = floatVar(floatVar(sampleTexture(texture2d("tOceanFloorDepth"), refractUv).x))
                oceanDepth2 set clamp(getLinearDepth(oceanDepth2, camData.clipNear, camData.clipFar) - fragDepth, 0f.const, 50f.const)

                val waveMod = dot(bumpNormal, Vec3f.X_AXIS.const)

                val oceanAlpha = clamp((oceanDepth2 - 0.5f.const) / 12f.const, 0f.const, 1f.const)
                val baseColor = float4Var(sampleTexture(texture1d("tOceanGradient"), oceanDepth2 / 50f.const + waveMod))
                val oceanFloorColor = float4Var(Vec4f.ZERO.const)
                `if`(oceanAlpha lt 1f.const) {
                    oceanFloorColor set sampleTexture(texture2d("tOceanFloorColor"), refractUv)
                    oceanFloorColor.rgb set convertColorSpace(oceanFloorColor.rgb, ColorSpaceConversion.sRGB_TO_LINEAR)
                }

                val waterColor = mix(oceanFloorColor, baseColor, oceanAlpha)
                val foamColor = float4Var(Vec4f(1f).const)
                oceanDepth1 set oceanDepth1 * clamp(dot(camToFrag, worldNormal), 0.5f.const, 1f.const)
                foamColor.a set 1f.const - smoothStep(0.2f.const, 0.25f.const, oceanDepth1)

                baseColorPort.input(float4Value(mix(waterColor, foamColor, foamColor.a).rgb, 1f))
            }
        }
    }
}