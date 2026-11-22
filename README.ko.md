# Makeium

맵 제작자를 위한 마인크래프트 26.3 올인원 도구 모드입니다. NeoForge와 Fabric에서 jar 하나로 돌아가고, 순수 Java라 실행 파일이 없습니다.
Makeium이 만든 결과물은 디스플레이 엔티티·데이터팩·리소스팩·커맨드 블록처럼 **모두 바닐라 형식**이라, 완성된 맵은 Makeium 없이 플레이할 수 있습니다. 모드는 아이템이나 블록을 등록하지 않으므로 Makeium 서버에 바닐라 클라이언트도 접속할 수 있습니다.

`/makeium`(또는 **M** 키)로 허브를 엽니다. 편집에는 OP 2 권한이 필요하며, 싱글플레이에서는 치트 허용이면 됩니다.

## 기능

| 기능 | 명령어 | 하는 일 |
|---|---|---|
| 디스플레이 팩토리 | `/makeium display` | 텍스트·블록·아이템 디스플레이를 블록처럼 설치합니다. 벽이나 바닥을 클릭하면 그 면에 딱 붙고 가운데에 정렬됩니다(표지판). 편집기에서 리치 텍스트(구간별 색·서식·**리소스팩 폰트**), 배경, 크기·회전, 보간, 키프레임 애니메이션(→ 함수로 내보내기)을 다룹니다. |
| 리소스팩 팩토리 | `/makeium resourcepack` | `resourcepacks/`에 팩을 만들어 바로 적용합니다. 텍스처 브라우저와 픽셀 편집기(애니메이션 프레임, PNG 끌어다 놓기), 커스텀 아이템(`item_model`), 폰트 아이콘, 사운드(.ogg 끌어다 놓기), 번역, 파일 편집을 지원하고, zip이나 월드 리소스팩으로 내보냅니다. |
| 데이터팩 팩토리 | `/makeium datapack` | 스크래치 같은 블록 코딩으로 이벤트(로드, 틱, 접속, 사망, 트리거, 클릭, 구역, 대화, 발전과제)와 104개 블록을 조합합니다. 서버의 명령어 트리에서 **모든 26.3 명령어**를 블록으로 쓸 수 있습니다. 26.3의 모든 데이터팩 파일 종류를 편집할 수 있고(레시피는 제작대 그리드), 서버의 실제 codec으로 검증합니다. |
| 트리거 구역 | `/makeium regions` | 구역에 들어오거나 나갈 때, 또는 안에 있는 동안 명령과 블록 코딩 이벤트를 실행합니다. |
| 영역 편집 | `/makeium edit` | 채우기, 벽, 바꾸기, 비우기, 복사·붙여넣기(회전·뒤집기), 이동, 되돌리기. |
| NPC·대화 | `/makeium npc` | 26.3 대화창으로 선택지가 있는 NPC를 만듭니다. 선택지는 `/trigger`로 동작하므로 일반 플레이어도 쓸 수 있습니다. |
| 카메라 컷신 | `/makeium camera` | 시점을 찍어 부드러운(Catmull-Rom) 컷신 함수를 만듭니다. |
| 스코어보드 대시보드 | `/makeium scoreboard` | 목표·점수·팀·보스바·저장소를 표로 보고 고칩니다. |
| 테스트 체크포인트 | `/makeium checkpoint` | 선택 영역을 저장했다가 한 번에 되돌립니다. |
| 맵 배포 패키징 | `/makeium package` | 플레이어 데이터와 작업 파일을 빼고 배포용 zip을 만듭니다. |
| 커맨드 블록 편집기 | 커맨드 블록 열기 | 여러 줄 편집, 실시간 검증·자동완성, 체인 보기. 바닐라 편집기로 바꿀 수 있습니다. |

도구는 `/makeium tool <id>`로 받습니다(`text_display`, `block_display`, `item_display`, `display_select`, `selection`, `region`, `link`, `npc`, `camera`). 도구는 `custom_data`가 붙은 바닐라 아이템입니다.

## 빌드

```bash
./gradlew build
```

결과물: `build/libs/makeium-<버전>+mc26.3.jar` (NeoForge·Fabric 공용).
개발 실행은 `./gradlew :neoforge:runClient` 또는 `./gradlew :fabric:runClient`입니다. `-Pmakeium.devScript=<파일>`을 주면 자동 테스트 스크립트를 실행합니다(`DevAutomation` 참고).

## 로고

마인크래프트 다이아몬드 곡괭이 텍스처의 색을 초록으로 바꾼 것입니다(`branding/gen_logo.py`). AI 생성 그림이 아니므로 모든 스토어에 같은 jar를 올립니다.

## 라이선스

Apache-2.0. 함께 들어 있는 Noto Sans KR·Courier Prime 폰트는 SIL OFL 1.1입니다(`docs/license/`).
