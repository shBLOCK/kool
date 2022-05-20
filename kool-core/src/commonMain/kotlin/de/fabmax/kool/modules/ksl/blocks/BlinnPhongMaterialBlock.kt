package de.fabmax.kool.modules.ksl.blocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.lang.*

fun KslScopeBuilder.blinnPhongMaterialBlock(block: BlinnPhongMaterialBlock.() -> Unit): BlinnPhongMaterialBlock {
    val blinnPhongMaterialBlock = BlinnPhongMaterialBlock(parentStage.program.nextName("blinnPhongMaterialBlock"), this)
    ops += blinnPhongMaterialBlock.apply(block)
    return blinnPhongMaterialBlock
}

class BlinnPhongMaterialBlock(name: String, parentScope: KslScopeBuilder) : LitMaterialBlock(name, parentScope) {
    val inAmbientColor = inFloat3("inAmbientColor")
    val inSpecularColor = inFloat3("inSpecularColor")
    val inShininess = inFloat1("inShininess", KslValueFloat1(16f))
    val inSpecularStrength = inFloat1("inSpecularStrength", KslValueFloat1(0f))

    init {
        body.apply {
            val ambientColor = float3Var(inBaseColor * inAmbientColor)
            val diffuseColor = float3Var(Vec3f.ZERO.const)
            val specularColor = float3Var(Vec3f.ZERO.const)
            val viewDir = float3Var(normalize(inCamPos - inFragmentPos))

            fori (0.const, inLightCount) { i ->
                val lightDir = float3Var(normalize(getLightDirectionFromFragPos(inFragmentPos, inEncodedLightPositions[i])))

                `if` (inShadowFactors[i] gt 0f.const) {
                    val lambertian = floatVar(max(dot(lightDir, inNormal), 0f.const))
                    val specular = floatVar(0f.const)
                    `if` (lambertian gt 0f.const) {
                        val halfDir = float3Var(normalize(lightDir + viewDir))
                        val specAngle = floatVar(max(dot(halfDir, inNormal), 0f.const))
                        specular set pow(specAngle, inShininess) * inSpecularStrength
                    }

                    val radiance = float3Var(inShadowFactors[i] * inLightStrength *
                            getLightRadiance(inFragmentPos, inEncodedLightPositions[i], inEncodedLightDirections[i], inEncodedLightColors[i]))

                    diffuseColor += inBaseColor * lambertian * radiance
                    specularColor += inSpecularColor * specular * radiance
                }
            }

            outColor set ambientColor + diffuseColor + specularColor
        }
    }
}