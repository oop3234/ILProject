# AIoT 실내 길안내 프로젝트 파일 구조 지침

이 문서는 Codex가 본 프로젝트의 파일 구조, 데이터 책임, 명명 규칙, 모듈 간 연결 방식을 일관되게 이해하고 구현하도록 하기 위한 프로젝트 지침이다.

---

## 1. 프로젝트 목표

본 프로젝트는 대학 건물 내부에서 사용자의 현재 위치를 추정하고, 목적지까지 경로를 계산하여 음성 안내하는 실내 길안내 시스템이다.

핵심 구성 요소는 다음과 같다.

1. 실측 기반 실내 지도 데이터
2. 기준점(Anchor / Reference Node) 이미지 데이터
3. 이미지 기반 현재 위치 추정
4. PDR(Pedestrian Dead Reckoning) 기반 상대 이동 추정
5. A* 기반 경로 탐색
6. 이미지 위치추정 + PDR 위치융합
7. ArUco Marker 기반 보정 기능(선택 사항)

핵심 설계 원칙은 다음과 같다.

- 실측 지도, 경로 그래프, 이미지 데이터, PDR 설정, 위치융합 설정을 하나의 거대한 JSON에 합치지 않는다.
- 각 모듈은 독립적으로 관리하되 `node_id`를 공통 식별자로 사용한다.
- 실측 지도 좌표를 위치 관련 데이터의 기준값(Single Source of Truth)으로 사용한다.
- PDR은 절대 위치 추정 수단이 아니라 기준점 사이의 상대 이동 추정 수단으로 사용한다.
- 이미지 기반 위치 인식에 성공하면 PDR 누적 오차를 보정한다.
- ArUco는 필수 기능이 아니다. 사용하지 않아도 전체 시스템이 동작하도록 구현한다.

---

## 2. 권장 디렉터리 구조

```text
IndoorNavigation/
│
├─ data/
│  └─ building/
│     └─ fusion_science/
│        │
│        ├─ building.json
│        │
│        ├─ floor1/
│        │  ├─ floor1.map.json
│        │  ├─ floor1.route.json
│        │  ├─ floor1.visual.json
│        │  └─ images/
│        │     ├─ N001/
│        │     │  ├─ front.jpg
│        │     │  ├─ right.jpg
│        │     │  ├─ back.jpg
│        │     │  └─ left.jpg
│        │     └─ ...
│        │
│        └─ floor2/
│           ├─ floor2.map.json
│           ├─ floor2.route.json
│           ├─ floor2.visual.json
│           └─ images/
│              ├─ B1/
│              │  ├─ front.jpg
│              │  ├─ right.jpg
│              │  ├─ back.jpg
│              │  └─ left.jpg
│              ├─ B2/
│              ├─ B3/
│              ├─ B4/
│              └─ B5/
│
├─ localization/
│  │
│  ├─ visual/
│  │  ├─ features/
│  │  ├─ feature_index.json
│  │  └─ visual_config.json
│  │
│  ├─ pdr/
│  │  ├─ pdr_config.json
│  │  └─ calibration.json
│  │
│  ├─ fusion/
│  │  └─ fusion_config.json
│  │
│  └─ aruco/
│     └─ aruco_markers.json
│
├─ app/
│  └─ ...
│
└─ PROJECT_STRUCTURE.md
```

---

## 3. 공통 식별자 규칙

모든 주요 데이터는 `node_id`를 기준으로 연결한다.

예시:

```text
B1
B2
B3
B4
B5
```

동일한 기준점은 모든 파일에서 같은 ID를 사용해야 한다.

예를 들어 `B3`는 다음 파일에서 모두 같은 물리적 위치를 의미해야 한다.

```text
floor2.map.json
floor2.route.json
floor2.visual.json
feature_index.json
aruco_markers.json
```

Codex는 기존 ID를 임의로 변경하지 않는다.

잘못된 예:

```text
map.json      -> B3
route.json    -> NODE_3
visual.json   -> anchor03
```

올바른 예:

```text
map.json      -> B3
route.json    -> B3
visual.json   -> B3
```

---

## 4. 좌표계 규칙

실내 위치는 2차원 데카르트 좌표계를 사용한다.

단위는 기본적으로 `meter`를 사용한다.

권장 방향 정의:

```text
+Y = 기준 정면
+X = 기준 정면에서 오른쪽
-Y = 기준 정면의 반대
-X = 기준 정면에서 왼쪽
```

Heading 규칙:

```text
0°   = +Y
90°  = +X
180° = -Y
270° = -X
```

모든 모듈에서 동일한 방향 체계를 사용해야 한다.

Codex는 Android 센서 방위각, OpenCV 회전각, 지도상의 heading을 사용할 때 필요하면 내부 변환 함수를 만들되, 외부 데이터 표현은 위 규칙으로 통일한다.

---

# 5. `floorN.map.json`

## 목적

실제로 측정한 건물의 공간 정보와 기준점 좌표를 저장한다.

이 파일은 해당 층의 위치 좌표에 대한 기준 데이터이다.

다음 정보를 포함할 수 있다.

- 기준점 좌표
- 복도 중심선
- 방
- 출입문
- 계단
- 엘리베이터
- 벽
- 층 정보
- 좌표계 정보

예시:

```json
{
  "building_id": "fusion_science",
  "floor": 2,
  "coordinate_system": {
    "unit": "meter",
    "origin": "B1",
    "x_axis": "+X",
    "y_axis": "+Y"
  },
  "nodes": [
    {
      "id": "B1",
      "type": "reference",
      "position": {
        "x": 0.0,
        "y": 0.0
      }
    },
    {
      "id": "B2",
      "type": "reference",
      "position": {
        "x": 0.0,
        "y": 6.2
      }
    }
  ],
  "rooms": [
    {
      "id": "ROOM_204",
      "name": "204호",
      "entrance_node": "B5"
    }
  ],
  "doors": [],
  "walls": []
}
```

## 규칙

- 실제 측정 좌표를 우선한다.
- 경로 탐색 가중치 계산을 위해 좌표를 임의로 변경하지 않는다.
- 사진 경로나 이미지 특징 벡터를 이 파일에 직접 저장하지 않는다.
- PDR의 실시간 상태를 이 파일에 저장하지 않는다.
- A* 탐색용 연결 관계는 가능하면 `route.json`에 분리한다.

---

# 6. `floorN.route.json`

## 목적

A* 또는 다른 그래프 기반 경로 탐색을 위한 노드와 간선 정보를 저장한다.

예시:

```json
{
  "floor": 2,
  "nodes": [
    {
      "id": "B1",
      "map_node": "B1"
    },
    {
      "id": "B2",
      "map_node": "B2"
    }
  ],
  "edges": [
    {
      "from": "B1",
      "to": "B2",
      "distance": 6.2,
      "bidirectional": true,
      "type": "corridor"
    }
  ]
}
```

## 간선 권장 필드

```text
from
to
distance
bidirectional
type
accessible
```

필요하면 다음과 같은 필드도 추가할 수 있다.

```text
stairs
elevator
door
slope
restricted
wheelchair_accessible
```

## 규칙

- 경로 노드 ID는 가급적 지도 노드 ID를 그대로 사용한다.
- `distance`는 실제 실측 길이 또는 지도 좌표 기반 거리와 일관되어야 한다.
- 일방통행이 아니면 `bidirectional: true`를 사용한다.
- 사용자 안내용 그래프와 지도 기하 정보를 혼합하지 않는다.

---

# 7. `floorN.visual.json`

## 목적

기준점과 해당 위치에서 촬영한 이미지 파일을 연결한다.

예시:

```json
{
  "floor": 2,
  "anchors": [
    {
      "id": "B1",
      "map_node": "B1",
      "images": [
        {
          "file": "images/B1/front.jpg",
          "heading": 0
        },
        {
          "file": "images/B1/right.jpg",
          "heading": 90
        },
        {
          "file": "images/B1/back.jpg",
          "heading": 180
        },
        {
          "file": "images/B1/left.jpg",
          "heading": 270
        }
      ]
    }
  ]
}
```

## 규칙

- 기준점 좌표를 이 파일에 중복 저장하는 것보다 `map_node`로 `map.json`을 참조하는 것을 우선한다.
- 이미지마다 촬영 방향을 기록한다.
- 이미지 경로는 프로젝트 기준 상대 경로를 사용한다.
- 절대 경로(`C:\Users\...`)를 JSON에 저장하지 않는다.
- 이미지가 추가되어도 `map.json`의 좌표 체계는 변경하지 않는다.

---

# 8. 기준점 이미지 파일 구조

각 기준점별 디렉터리를 사용한다.

예:

```text
images/
├─ B1/
│  ├─ front.jpg
│  ├─ right.jpg
│  ├─ back.jpg
│  └─ left.jpg
├─ B2/
├─ B3/
├─ B4/
└─ B5/
```

향후 이미지가 많아질 경우 다음과 같이 확장 가능하다.

```text
B1/
├─ front_01.jpg
├─ front_02.jpg
├─ right_01.jpg
├─ right_02.jpg
├─ back_01.jpg
└─ left_01.jpg
```

조명 변화 대응용 이미지도 필요하면 추가한다.

예:

```text
B1_front_day_01.jpg
B1_front_evening_01.jpg
```

Codex는 이미지 파일명을 코드에 하드코딩하기보다 `visual.json`에서 읽도록 구현한다.

---

# 9. Visual Localization 구조

## 목표

현재 카메라 이미지와 사전에 촬영한 기준점 이미지를 비교하여 가장 가능성이 높은 기준점을 추정한다.

권장 흐름:

```text
Camera Frame
    ↓
Feature Extractor
    ↓
Current Feature Vector
    ↓
Feature DB 검색
    ↓
가장 유사한 기준점 이미지
    ↓
node_id 추정
    ↓
map.json에서 좌표 조회
```

사용 가능한 방식 예:

```text
DINOv2
AnyLoc
OpenCV local feature
Image Retrieval
```

프로젝트에서 특정 모델을 변경하더라도 데이터 구조는 유지한다.

---

# 10. Visual Feature 저장 구조

이미지마다 계산된 특징 벡터는 원본 이미지와 별도로 저장한다.

```text
localization/
└─ visual/
   ├─ features/
   │  ├─ B1_front.npy
   │  ├─ B1_right.npy
   │  ├─ B1_back.npy
   │  ├─ B1_left.npy
   │  └─ ...
   ├─ feature_index.json
   └─ visual_config.json
```

`feature_index.json` 예시:

```json
{
  "features": [
    {
      "node_id": "B1",
      "image": "data/building/fusion_science/floor2/images/B1/front.jpg",
      "feature": "features/B1_front.npy",
      "heading": 0
    }
  ]
}
```

## 규칙

- 특징 벡터 자체를 JSON 배열로 대량 저장하지 않는다.
- `.npy`, `.bin` 등 별도 바이너리 파일 사용을 우선한다.
- JSON에는 feature 파일의 위치와 metadata만 저장한다.
- 이미지 특징 모델이 변경되면 모델 버전을 metadata에 기록하는 것을 권장한다.

예:

```json
{
  "model": "dinov2",
  "model_version": "example-version",
  "dimension": 768
}
```

모델 버전 값은 실제 사용하는 모델을 확인한 뒤 입력한다.

---

# 11. PDR 구조

PDR은 기준점 사이의 상대 위치를 추정하는 역할을 담당한다.

핵심 입력:

```text
Accelerometer
Gyroscope
Rotation Vector
Step Detector
```

핵심 상태:

```text
x
y
heading
stepCount
distance
confidence
lastAnchor
```

권장 runtime 구조 예:

```json
{
  "position": {
    "x": 3.2,
    "y": 8.7
  },
  "heading": 5.3,
  "step_count": 13,
  "distance": 8.9,
  "last_anchor": "B2",
  "confidence": 0.74
}
```

주의:

이 정보는 일반적으로 정적 프로젝트 데이터가 아니라 앱 실행 중 유지되는 runtime state이다.

---

# 12. `pdr_config.json`

PDR 알고리즘 설정값을 저장한다.

예:

```json
{
  "step_length": {
    "default": 0.70,
    "adaptive": true
  },
  "heading": {
    "sensor": "rotation_vector",
    "initial_heading_required": true
  },
  "step_detection": {
    "sensor": "accelerometer"
  },
  "correction": {
    "enable_visual_anchor": true,
    "reset_position_at_anchor": true
  }
}
```

`0.70 m` 같은 값은 예시이며 실제 사용자의 보폭을 측정하거나 calibration을 통해 결정해야 한다.

Codex는 예시 수치를 실제 확정값으로 간주하지 않는다.

---

# 13. `calibration.json`

사용자 또는 기기별 PDR 보정값을 저장할 수 있다.

예:

```json
{
  "device": "android_phone",
  "step_length": 0.68,
  "heading_offset_deg": 2.5
}
```

향후 사용자별 프로필이 필요하면 별도 구조로 확장한다.

---

# 14. 위치 융합(Fusion)

위치 융합은 본 프로젝트의 핵심 모듈이다.

사용 가능한 위치 소스:

```text
VISUAL
PDR
ARUCO
MANUAL
```

권장 runtime 객체:

```json
{
  "position": {
    "x": 4.2,
    "y": 10.5,
    "floor": 2
  },
  "heading": 91.3,
  "source": "PDR",
  "confidence": 0.71,
  "last_anchor": "B3",
  "timestamp": 0
}
```

`timestamp` 값 형식은 플랫폼 전체에서 하나로 통일한다.

예:

```text
Unix milliseconds
```

또는

```text
ISO-8601
```

중 하나를 정하고 혼용하지 않는다.

---

# 15. 위치 융합 기본 로직

기본 흐름:

```text
기준점 이미지 인식
        ↓
node_id 확인
        ↓
map.json에서 절대 좌표 조회
        ↓
현재 위치를 해당 좌표로 보정
        ↓
PDR 시작
        ↓
걸음 + 방향으로 상대 위치 갱신
        ↓
다음 기준점 이미지 인식
        ↓
PDR 누적 오차 제거
        ↓
새 기준점 좌표로 다시 보정
```

즉:

```text
Anchor
  ↓
PDR
  ↓
PDR
  ↓
PDR
  ↓
Anchor
  ↓
Correction
  ↓
PDR
```

---

# 16. `fusion_config.json`

위치 융합 정책을 저장한다.

예시:

```json
{
  "priority": [
    "ARUCO",
    "VISUAL",
    "PDR"
  ],
  "visual": {
    "minimum_similarity": null
  },
  "pdr": {
    "maximum_distance_without_correction": null
  },
  "anchor_reset": {
    "enabled": true
  }
}
```

`minimum_similarity`나 최대 PDR 이동 거리 같은 임계값은 실제 실험 데이터를 통해 정해야 한다.

검증되지 않은 값을 Codex가 임의로 확정하지 않는다.

---

# 17. ArUco Marker

ArUco는 선택 기능이다.

전체 시스템은 ArUco가 없어도 실행 가능해야 한다.

사용하는 경우:

```text
localization/
└─ aruco/
   └─ aruco_markers.json
```

예:

```json
{
  "markers": [
    {
      "marker_id": 21,
      "node_id": "B3",
      "floor": 2
    }
  ]
}
```

ArUco가 감지되면:

```text
Marker ID
    ↓
node_id
    ↓
map.json 좌표 조회
    ↓
현재 위치 보정
```

## 구현 규칙

ArUco 모듈이 꺼져 있어도 다음이 동작해야 한다.

```text
Visual Localization
PDR
Route Search
Position Fusion
```

즉, ArUco dependency를 core module에 강제하지 않는다.

---

# 18. 전체 데이터 흐름

```text
                Indoor Map
                    │
                    │
              floor2.map.json
                    │
                    ↓
             Coordinate System
                    │
        ┌───────────┴───────────┐
        │                       │
        ↓                       ↓
 Visual Localization       Route Graph
        │                       │
        ↓                       ↓
 current node             A* Search
        │
        ↓
 absolute position
        │
        ↓
       PDR
        │
        ↓
 relative position
        │
        ↓
 Position Fusion
        │
        ↓
 Current Position
        │
        ↓
 Navigation Guidance
        │
        ↓
       TTS
```

---

# 19. 권장 소프트웨어 모듈

Codex가 코드 구조를 설계할 때 아래와 같이 기능을 분리하는 것을 권장한다.

```text
MapRepository
RouteRepository
VisualRepository

VisualLocalizer
PdrTracker
ArucoLocalizer

PositionFusionEngine
RoutePlanner
NavigationEngine
GuidanceGenerator
```

역할 예:

## MapRepository

```text
map.json 로드
node_id → 좌표 조회
room → entrance_node 조회
```

## RouteRepository

```text
route.json 로드
그래프 생성
edge 조회
```

## VisualRepository

```text
visual.json 로드
image ↔ node_id 관리
feature index 관리
```

## VisualLocalizer

```text
현재 이미지 feature 추출
DB feature 검색
후보 node 반환
```

## PdrTracker

```text
걸음 감지
heading 추정
상대 위치 업데이트
```

## PositionFusionEngine

```text
Visual / PDR / ArUco 결과 통합
현재 위치 결정
anchor 발견 시 PDR 보정
```

## RoutePlanner

```text
현재 위치 → 목적지
A* 실행
경로 반환
```

## NavigationEngine

```text
현재 위치
경로
heading
다음 이동 방향 결정
```

---

# 20. 데이터 모델 관계

```text
                    Node ID
                      B3
                       │
      ┌────────────────┼────────────────┐
      │                │                │
      ↓                ↓                ↓
   Map Data        Route Data       Visual Data
      │                │                │
  (x, y)        B2 ↔ B3 ↔ B4      B3 images
                                        │
                                        ↓
                                  Feature Vector
                                        │
                                        ↓
                                Visual Localization
                                        │
                                        ↓
                                  Position Reset
                                        │
                                        ↓
                                       PDR
```

`node_id`가 시스템 전체를 연결하는 핵심 키이다.

---

# 21. 파일 명명 규칙

권장:

```text
floor2.map.json
floor2.route.json
floor2.visual.json
```

이미지:

```text
front.jpg
right.jpg
back.jpg
left.jpg
```

feature:

```text
B1_front.npy
B1_right.npy
B1_back.npy
B1_left.npy
```

코드:

```text
MapRepository.kt
RouteRepository.kt
VisualLocalizer.kt
PdrTracker.kt
PositionFusionEngine.kt
RoutePlanner.kt
```

가능하면 동일 개념에 대해 여러 명명 방식을 섞지 않는다.

---

# 22. JSON 설계 공통 규칙

Codex는 JSON을 수정할 때 다음 규칙을 따른다.

1. 기존 키 이름을 임의로 변경하지 않는다.
2. 좌표 단위를 항상 명시한다.
3. 동일 node는 동일 ID를 사용한다.
4. 절대 파일 경로를 저장하지 않는다.
5. 이미지 원본과 feature vector를 JSON 안에 직접 넣지 않는다.
6. runtime PDR 데이터를 정적 map JSON에 저장하지 않는다.
7. 경로 그래프와 실측 지도 데이터를 가능한 한 분리한다.
8. 임계값은 실험 없이 임의 확정하지 않는다.
9. 새로운 필드가 필요하면 기존 구조와 backward compatibility를 고려한다.
10. JSON 로딩 시 필수 필드와 선택 필드를 구분한다.

---

# 23. Codex 구현 시 금지 사항

다음을 하지 않는다.

### 1. 지도 좌표 임의 변경

실측값이 제공된 경우 코드 편의를 위해 수정하지 않는다.

### 2. 모든 데이터를 하나의 JSON으로 합치기

다음 구조는 피한다.

```text
all_data.json
```

안에:

```text
map
route
images
features
pdr
aruco
runtime state
```

를 모두 저장하지 않는다.

### 3. 파일 경로 하드코딩

잘못된 예:

```kotlin
"C:\\Users\\user\\Desktop\\AIot\\B1\\front.jpg"
```

대신 config 또는 repository를 통해 상대경로를 관리한다.

### 4. PDR 단독 절대 위치로 간주

PDR은 drift가 누적될 수 있으므로 기준점 기반 보정이 필요한 상대 위치 추정 수단으로 취급한다.

### 5. Visual 결과를 무조건 신뢰

이미지 유사도가 낮거나 후보 간 차이가 충분하지 않으면 위치를 확정하지 않는다.

### 6. ArUco를 필수 dependency로 만들기

ArUco는 선택 기능이다.

---

# 24. 위치 추정 결과 인터페이스 권장안

각 localization module의 결과 형식을 최대한 통일한다.

예:

```json
{
  "source": "VISUAL",
  "floor": 2,
  "node_id": "B3",
  "position": {
    "x": 0.0,
    "y": 12.5
  },
  "heading": 0.0,
  "confidence": 0.92,
  "timestamp": 0
}
```

PDR 예:

```json
{
  "source": "PDR",
  "floor": 2,
  "node_id": null,
  "position": {
    "x": 0.2,
    "y": 10.8
  },
  "heading": 3.4,
  "confidence": 0.70,
  "timestamp": 0
}
```

이렇게 하면 `PositionFusionEngine`이 동일한 형식으로 위치 후보를 처리할 수 있다.

---

# 25. 단계별 구현 우선순위

Codex는 가능하면 아래 순서를 기준으로 구현한다.

## Phase 1

```text
map.json loader
route.json loader
A* route search
```

목표:

```text
B1 → B5
```

경로를 정상적으로 계산할 수 있어야 한다.

## Phase 2

```text
PDR
```

목표:

```text
기준점 + heading
        ↓
걸음 이동
        ↓
현재 좌표 갱신
```

## Phase 3

```text
Visual Localization
```

목표:

```text
camera image
    ↓
reference image retrieval
    ↓
node_id 추정
```

## Phase 4

```text
Visual + PDR Fusion
```

목표:

```text
Visual anchor
     ↓
PDR
     ↓
Visual anchor
     ↓
PDR correction
```

## Phase 5

```text
Navigation Engine
TTS
```

## Phase 6

필요한 경우:

```text
ArUco
```

추가.

---

# 26. 최종 시스템 동작 예

사용자가 204호까지 길 안내를 요청했다고 가정한다.

```text
1. Camera 실행

2. Visual Localization
   → B1 인식

3. MapRepository
   → B1 좌표 조회
   → (0, 0)

4. Destination
   → ROOM_204
   → entrance_node = B5

5. RoutePlanner
   → A*
   → B1 → B2 → B3 → B4 → B5

6. PDR 시작

7. 사용자가 이동

8. PDR
   → 상대 위치 지속 갱신

9. 카메라가 B3 기준점 인식

10. 현재 위치를 B3 좌표로 보정

11. PDR 누적 오차 초기화/보정

12. B4 → B5 방향으로 계속 안내

13. B5 도착

14. 목적지 도착 안내
```

---

# 27. Codex가 코드 수정 전에 확인할 것

코드를 새로 작성하거나 수정할 때 다음을 먼저 확인한다.

```text
1. 이 코드가 어느 데이터 파일을 사용하는가?
2. node_id 규칙을 유지하는가?
3. 좌표계 방향이 기존 규칙과 일치하는가?
4. map 데이터와 route 데이터를 혼합하고 있지 않은가?
5. PDR state와 static map data를 혼합하고 있지 않은가?
6. feature/image 경로가 상대경로인가?
7. ArUco 없이도 실행 가능한가?
8. 기존 JSON과 호환되는가?
```

---

# 28. 프로젝트의 핵심 원칙 요약

Codex는 다음 원칙을 가장 중요하게 취급한다.

```text
실측 지도 = 절대 좌표 기준

Visual Localization = 기준점 식별 / 절대 위치 보정

PDR = 기준점 사이 상대 이동 추정

Route Graph = A* 경로 탐색

Position Fusion = Visual + PDR 통합

ArUco = 선택적 고정밀 보정 수단
```

전체 데이터 연결의 중심은:

```text
node_id
```

이다.

최종 목표 구조:

```text
실측 지도
   ↓
기준점 이미지 DB
   ↓
Visual Localization
   ↓
절대 위치 확인
   ↓
PDR 상대 추적
   ↓
위치 융합
   ↓
A* 경로
   ↓
실시간 안내
   ↓
TTS
```

Codex는 앞으로 본 프로젝트에서 파일 생성, 코드 생성, 데이터 수정 시 이 문서를 기본 구조 지침으로 따른다.
