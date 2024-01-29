package de.fabmax.kool.pipeline.backend.webgpu

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.configJs
import de.fabmax.kool.scene.Scene

class WgpuScreenRenderPass(backend: RenderBackendWebGpu) :
    WgpuRenderPass(GPUTextureFormat.depth32float, KoolSystem.configJs.numSamples, backend)
{
    private val canvasContext: GPUCanvasContext
        get() = backend.canvasContext

    private var colorTexture: GPUTexture? = null
    private var colorTextureView: GPUTextureView? = null

    private var depthAttachment: GPUTexture? = null
    private var depthAttachmentView: GPUTextureView? = null

    fun onCanvasResized(newWidth: Int, newHeight: Int) {
        updateRenderTextures(newWidth, newHeight)
    }

    fun renderScene(scenePass: Scene.OnscreenSceneRenderPass) {
        if (depthAttachment == null || colorTexture == null) {
            updateRenderTextures(backend.canvas.width, backend.canvas.height)
        }

        val colorAttachment = arrayOf(
            GPURenderPassColorAttachment(
                view = colorTextureView!!,
                clearValue = scenePass.clearColor?.let { GPUColorDict(it) },
                resolveTarget = canvasContext.getCurrentTexture().createView()
            )
        )

        render(scenePass, colorAttachment, depthAttachmentView)
    }

    private fun updateRenderTextures(width: Int, height: Int) {
        colorTexture?.destroy()
        depthAttachment?.destroy()

        val colorDescriptor = GPUTextureDescriptor(
            size = intArrayOf(width, height),
            format = backend.canvasFormat,
            usage = GPUTextureUsage.RENDER_ATTACHMENT,
            sampleCount = multiSamples
        )
        colorTexture = device.createTexture(colorDescriptor).also {
            colorTextureView = it.createView()
        }

        val depthDescriptor = GPUTextureDescriptor(
            size = intArrayOf(width, height),
            format = depthFormat!!,
            usage = GPUTextureUsage.RENDER_ATTACHMENT,
            sampleCount = multiSamples
        )
        depthAttachment = device.createTexture(depthDescriptor).also {
            depthAttachmentView = it.createView()
        }
    }

}