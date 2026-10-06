import * as THREE from 'three'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'
import { sceneQuality, studioEntry, studioWind } from './evidenceSceneMath'
import type { SceneQuality } from './evidenceSceneMath'

export type { SceneQuality } from './evidenceSceneMath'
export type SceneLight = 'daylight' | 'cloud'
export interface EvidenceScene {
  resize(width: number, height: number): void
  render(time: number, pointerX: number, pointerY: number): void
  setQuality(quality: SceneQuality): void
  setLight(light: SceneLight): void
  dispose(): void
}

// One deterministic field drives paper corners and the light. No random clocks or image requests.
export function createEvidenceScene(canvas: HTMLCanvasElement, quality: SceneQuality): EvidenceScene {
  const gl = canvas.getContext('webgl2', { alpha: true, antialias: true, powerPreference: 'low-power' })
  if (!gl) throw new Error('WebGL2 unavailable')
  const renderer = new THREE.WebGLRenderer({ canvas, context: gl, alpha: true, antialias: true })
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.18
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  const scene = new THREE.Scene()
  const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 35)
  camera.position.set(0, 0, 8.9)
  const world = new THREE.Group()
  scene.add(world)
  const sky = new THREE.HemisphereLight(0xf1f6ff, 0xf4eee1, 2.8)
  scene.add(sky)
  const fill = new THREE.DirectionalLight(0xe9f3ff, 1.2)
  fill.position.set(4, 1, 7)
  scene.add(fill)
  const sun = new THREE.DirectionalLight(0xffedd2, 3)
  sun.position.set(-3.5, 6, 8)
  sun.castShadow = true
  Object.assign(sun.shadow.camera, { left: -5, right: 5, top: 5, bottom: -5, near: 0.1, far: 20 })
  sun.shadow.bias = -0.0005
  sun.shadow.normalBias = 0.025
  sun.shadow.radius = 4
  scene.add(sun)
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(20, 20), new THREE.ShadowMaterial({ opacity: 0.14 }))
  ground.position.z = -0.8
  ground.receiveShadow = true
  world.add(ground)
  const textures: THREE.Texture[] = []
  const paperFaces: Array<{ geometry: THREE.PlaneGeometry; original: Float32Array; phase: number }> = []
  const paperGroups: Array<{ object: THREE.Group; kind: 'resume' | 'project' | 'skills'; y: number }> = []
  let composer: EffectComposer | null = null
  let optics: ShaderPass | null = null
  let disposed = false
  let cloudy = false
  let level = quality
  let width = 600
  let height = 560

  function texture(kind: 'resume' | 'project' | 'skills') {
    const sheet = document.createElement('canvas')
    sheet.width = 768
    sheet.height = kind === 'resume' ? 1086 : 414
    const ctx = sheet.getContext('2d')!
    ctx.fillStyle = '#fffdf8'
    ctx.fillRect(0, 0, sheet.width, sheet.height)
    ctx.fillStyle = '#203a4d'
    ctx.font = '600 42px "Microsoft YaHei", sans-serif'
    ctx.fillText(kind === 'resume' ? '让真实经历被看见' : kind === 'project' ? '项目成果' : '已确认技能', 60, 90)
    ctx.fillStyle = '#667383'
    ctx.font = '24px "Microsoft YaHei", sans-serif'
    ctx.fillText(kind === 'resume' ? '我的求职简历  /  RESUME' : kind === 'project' ? '从一段经历，找到可验证的价值' : '把能力与真实经历连接起来', 60, 136)
    ctx.fillStyle = '#2563eb'
    ctx.fillRect(60, 173, kind === 'resume' ? 648 : 60, 5)
    const sections = kind === 'resume' ? ['个人简介', '项目经历', '教育背景', '专业技能'] : [kind === 'project' ? '整理经历 · 留下证据' : '需求分析    用户研究    数据表达']
    sections.forEach((title, index) => {
      const y = 244 + index * 198
      ctx.fillStyle = '#274751'
      ctx.font = `600 ${kind === 'resume' ? 26 : 25}px "Microsoft YaHei", sans-serif`
      ctx.fillText(title, 60, y)
      ctx.fillStyle = '#d9e1e4'
      for (let line = 0; line < (kind === 'resume' ? 3 : 2); line++) {
        ctx.fillRect(60, y + 31 + line * 30, line === 2 ? 416 : line === 1 ? 590 : 640, 9)
      }
    })
    ctx.fillStyle = '#527a75'
    ctx.font = '20px "Microsoft YaHei", sans-serif'
    ctx.fillText('JOBPROOF AI   ·   示例内容', 60, sheet.height - 38)
    const result = new THREE.CanvasTexture(sheet)
    result.colorSpace = THREE.SRGBColorSpace
    result.anisotropy = Math.min(4, renderer.capabilities.getMaxAnisotropy())
    textures.push(result)
    return result
  }

  function paper(kind: 'resume' | 'project' | 'skills', x: number, y: number, z: number, w: number, h: number, angle: number) {
    const group = new THREE.Group()
    group.position.set(x, y, z)
    group.rotation.z = angle
    const back = new THREE.Mesh(new THREE.BoxGeometry(w, h, 0.018), new THREE.MeshStandardMaterial({ color: 0xfffdf8, roughness: 0.85, metalness: 0, transparent: true }))
    back.castShadow = true
    back.receiveShadow = true
    group.add(back)
    const geometry = new THREE.PlaneGeometry(w, h, 20, 24)
    const face = new THREE.Mesh(geometry, new THREE.MeshStandardMaterial({ map: texture(kind), roughness: 0.85, metalness: 0, transparent: true, side: THREE.DoubleSide, emissive: 0xfff8e9, emissiveIntensity: 0.09 }))
    face.position.z = 0.013
    face.castShadow = true
    face.receiveShadow = true
    group.add(face)
    paperFaces.push({ geometry, original: Float32Array.from(geometry.attributes.position!.array), phase: x * 0.6 })
    world.add(group)
    paperGroups.push({ object: group, kind, y })
  }
  paper('project', -1.52, 0.44, -0.15, 2.25, 1.21, 0.045)
  paper('resume', 0.34, 0.17, 0.38, 2.7, 3.82, -0.07)
  paper('skills', 1.32, -1.45, 0.75, 2.12, 1.14, 0.045)

  const path = new THREE.CatmullRomCurve3([
    new THREE.Vector3(-2.75, 1.55, -0.45), new THREE.Vector3(-1.9, 1.9, -0.45),
    new THREE.Vector3(-0.6, 1.9, -0.45), new THREE.Vector3(2.35, 1.4, -0.45),
    new THREE.Vector3(2.6, 0.05, -0.45), new THREE.Vector3(2.7, -0.5, -0.45),
  ])
  const route = new THREE.Mesh(new THREE.TubeGeometry(path, 60, 0.014, 6, false), new THREE.MeshStandardMaterial({ color: 0x84aeb9, roughness: 0.8, transparent: true }))
  world.add(route)
  const routeNodes: THREE.Mesh[] = []
  for (const t of [0, 0.36, 1]) {
    const node = new THREE.Mesh(new THREE.SphereGeometry(0.05, 12, 8), new THREE.MeshStandardMaterial({ color: 0x2563eb, roughness: 0.7 }))
    node.position.copy(path.getPoint(t))
    world.add(node)
    routeNodes.push(node)
  }

  // A translucent cloud shadow crosses only the rear decorative plane, never the DOM or paper text.
  const cloud = new THREE.Mesh(new THREE.PlaneGeometry(13, 10), new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, uniforms: { weather: { value: 0 }, strength: { value: 0 } },
    vertexShader: 'varying vec2 vUv; void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}',
    fragmentShader: 'varying vec2 vUv; uniform float weather; uniform float strength; void main(){vec2 p=vUv-vec2(.4+weather*.08,.6);float shade=exp(-dot(p*vec2(3.5,6.0),p*vec2(3.5,6.0)));gl_FragColor=vec4(.42,.54,.55,shade*strength);}',
  }))
  cloud.position.z = -0.82
  world.add(cloud)

  function releaseOptics() {
    composer?.passes.forEach(pass => pass.dispose())
    composer?.dispose()
    composer = null
    optics = null
  }
  function createOptics() {
    if (composer) return
    composer = new EffectComposer(renderer)
    for (const target of [composer.renderTarget1, composer.renderTarget2]) {
      target.depthTexture = new THREE.DepthTexture(target.width, target.height, THREE.UnsignedIntType)
    }
    optics = new ShaderPass({
      uniforms: { tDiffuse: { value: null }, tDepth: { value: null }, texel: { value: new THREE.Vector2() }, focus: { value: 8.52 }, near: { value: camera.near }, far: { value: camera.far } },
      vertexShader: 'varying vec2 vUv;void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}',
      fragmentShader: `
        varying vec2 vUv; uniform sampler2D tDiffuse; uniform sampler2D tDepth;
        uniform vec2 texel; uniform float focus; uniform float near; uniform float far;
        float accent(vec3 c){return smoothstep(.07,.3,c.b-c.r)*smoothstep(.2,.8,c.b);}
        void main(){
          vec4 base=texture2D(tDiffuse,vUv);float d=texture2D(tDepth,vUv).x;
          float distance=near*far/(far-d*(far-near));
          float blur=smoothstep(focus+.24,focus+1.1,distance)*1.5;
          vec4 soft=base;vec3 glint=vec3(0.0);
          for(int i=0;i<8;i++){
            float a=float(i)*.785398;vec2 offset=vec2(cos(a),sin(a))*texel;
            soft+=texture2D(tDiffuse,vUv+offset*blur);
            vec3 c=texture2D(tDiffuse,vUv+offset*2.0).rgb;glint+=c*accent(c);
          }
          vec4 result=mix(base,soft/9.0,smoothstep(.0,1.5,blur)*.7);
          result.rgb+=glint*.007;gl_FragColor=vec4(result.rgb,base.a);
        }`,
    })
    composer.addPass(new RenderPass(scene, camera))
    composer.addPass(optics)
    composer.addPass(new OutputPass())
  }

  const resize = (w: number, h: number) => {
    width = Math.max(1, w)
    height = Math.max(1, h)
    if (disposed) return
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, sceneQuality[level].dpr))
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.position.z = camera.aspect < 1.05 ? 9.8 : 8.9
    camera.updateProjectionMatrix()
    composer?.setPixelRatio(renderer.getPixelRatio())
    composer?.setSize(width, height)
    if (optics) {
      optics.uniforms.texel!.value.set(1 / (width * renderer.getPixelRatio()), 1 / (height * renderer.getPixelRatio()))
      optics.uniforms.focus!.value = camera.position.z - 0.38
    }
  }
  const setQuality = (value: SceneQuality) => {
    level = value
    const size = sceneQuality[level].shadowSize
    if (sun.shadow.mapSize.x !== size) {
      sun.shadow.map?.dispose()
      sun.shadow.map = null
      sun.shadow.mapSize.set(size, size)
    }
    if (sceneQuality[level].postprocessing) createOptics()
    else releaseOptics()
    resize(width, height)
  }
  setQuality(level)
  return {
    resize,
    setQuality,
    setLight(light) { cloudy = light === 'cloud' },
    render(time, px, py) {
      if (disposed || renderer.getContext().isContextLost()) return
      const wind = studioWind(time)
      const entry = studioEntry(time)
      sun.intensity = cloudy ? 2.3 : 3
      sky.intensity = cloudy ? 3.1 : 2.8
      cloud.material.uniforms.weather!.value = wind
      cloud.material.uniforms.strength!.value = level === 'standard' ? 0 : cloudy ? 0.06 : 0.035
      world.rotation.y = px * 0.022
      world.rotation.x = py * 0.018
      paperFaces.forEach(({ geometry, original, phase }) => {
        const position = geometry.attributes.position!
        for (let i = 0; i < position.count; i++) {
          const x = original[i * 3]!
          const y = original[i * 3 + 1]!
          const corner = Math.max(0, x / 1.4) ** 3 * Math.max(0, y / 1.8) ** 2
          position.setZ(i, corner * (0.028 + studioWind(time, phase) * 0.012))
        }
        position.needsUpdate = true
        geometry.computeVertexNormals()
      })
      paperGroups.forEach(({ object, kind, y }) => {
        const amount = entry[kind]
        object.visible = amount > 0
        // Paper's settled position never floats; only evidence translates during first entry.
        object.position.y = y - (kind === 'resume' ? 0 : (1 - amount) * 0.12)
        object.children.forEach(child => { if (child instanceof THREE.Mesh) child.material.opacity = amount })
      })
      route.geometry.setDrawRange(0, Math.floor((route.geometry.index?.count ?? 0) * entry.path / 36) * 36)
      route.material.opacity = entry.path
      routeNodes.forEach((node, index) => { node.visible = entry.path >= (index + 1) / 3 })
      if (composer && optics) {
        optics.uniforms.tDepth!.value = composer.readBuffer.depthTexture
        composer.render()
      } else renderer.render(scene, camera)
    },
    dispose() {
      if (disposed) return
      disposed = true
      releaseOptics()
      scene.traverse(object => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose()
          const materials = Array.isArray(object.material) ? object.material : [object.material]
          materials.forEach(material => material.dispose())
        }
      })
      textures.forEach(item => item.dispose())
      sun.shadow.map?.dispose()
      renderer.dispose()
      // The component can reuse this canvas after a mobile/reduced-motion resize.
      // Forcing context loss here would poison that next mount; all GPU resources above are disposed.
    },
  }
}
