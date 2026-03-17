## 백엔드 패키지 구조 규칙

### 1. 기본 원칙
- 기능별 패키지 구조를 사용한다.
- 현재는 `domain` 아래에서만 작업한다.
- 공통 패키지(global, common, auth, config)는 추후 필요 시 분리한다.

### 2. 도메인 구조
각 기능 폴더 내부는 아래 구조를 기본으로 한다.

- controller
- service
- repository
- entity
- dto
    - request
    - response

### 3. 현재 도메인 구분
- user : 회원가입, 로그인, 프로필
- hiking : 등산 시작/종료, GPS 기록
- summit : 정상 조회, 정상 인증
- trail : 등산로/난이도 조회
- stats : 사용자 통계 조회

### 4. 작업 규칙
- 본인 담당 기능 폴더에서 우선 작업한다.
- 애매한 공통 폴더는 임의로 만들지 않는다.
- 구조 변경이 필요하면 먼저 공유 후 반영한다.