# 백엔드 API 응답 형식 통일 가이드

## 1. 목적

도메인별로 API를 따로 만들다 보면 응답 형식이 제각각이 될 수 있다.  
이 문서는 모든 API의 응답 형식을 통일해서 프론트 연동과 유지보수를 쉽게 하기 위한 기준이다.

---

## 2. 핵심 개념 (가장 중요)

API 응답은 항상 두 층으로 생각한다.

1. 바깥 형식 → ApiResponse
2. 안쪽 데이터 → DTO

---

## 3. 공통 규칙

### 3-1. 모든 API는 ApiResponse로 감싼다

모든 API는 아래 형태를 따른다.

{
"success": true,
"message": "조회 성공",
"data": ...
}

여기서 중요한 점:

- DTO를 없애는 것이 아니다
- DTO를 data 안에 넣는 것이다

즉 구조는 항상 이거다:

ApiResponse<DTO>

---

### 3-2. DTO는 그대로 사용한다

예:

- 사용자 조회 → UserResponse
- 세션 조회 → SessionResponse
- 통계 조회 → StatsResponse

형식:

ResponseEntity<ApiResponse<SomeResponseDto>>

---

## 4. 지도 응답 규칙

### 4-1. 지도용 API는 GeoJSON을 사용한다

지도는 좌표 데이터를 사용하기 때문에  
지도에 바로 사용할 응답은 GeoJSON 형식으로 맞춘다.

GeoJSON 기본 구조:

{
"type": "FeatureCollection",
"features": [
{
"type": "Feature",
"geometry": {
"type": "Point",
"coordinates": [127.301, 36.351]
},
"properties": {
"id": 1,
"name": "sample"
}
}
]
}

---

### 4-2. 지도 API의 최종 구조

지도 API도 ApiResponse를 사용한다.

{
"success": true,
"message": "지도 조회 성공",
"data": {
"type": "FeatureCollection",
"features": [...]
}
}

즉:

ApiResponse<GeoJsonFeatureCollectionResponse>

---

## 5. 왜 이렇게 나누는가

### ApiResponse 역할

- 모든 API의 바깥 구조를 통일
- success / message / data 위치 고정

### GeoJSON 역할

- 지도 데이터 구조 통일
- point / line 표현을 표준화

---

## 6. 자주 하는 실수

### 실수 1

DTO를 그대로 반환하는 것

잘못된 예:
ResponseEntity<SomeResponseDto>

올바른 예:
ResponseEntity<ApiResponse<SomeResponseDto>>

---

### 실수 2

지도 응답을 일반 DTO로 반환하는 것

지도는 GeoJSON 형식을 사용해야 한다.

---

### 실수 3

일반 조회 응답을 지도에 그대로 사용하는 것

일반 조회 DTO와 지도 DTO는 목적이 다르므로 분리해야 한다.

---

## 7. 좌표 규칙

좌표는 반드시 아래 순서를 따른다.

[longitude, latitude]

- 경도 먼저
- 위도 나중

---

## 8. 최종 정리

- ApiResponse = 모든 API의 바깥 형식
- DTO = 실제 데이터 내용
- GeoJSON DTO = 지도용 데이터 내용

즉 모든 API는 아래 구조로 간다.

ApiResponse<DTO>

지도 API는 아래 구조로 간다.

ApiResponse<GeoJsonFeatureCollectionResponse>
