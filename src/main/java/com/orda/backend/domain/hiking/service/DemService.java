package com.orda.backend.domain.hiking.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.DirectPosition2D;
import org.opengis.geometry.DirectPosition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
public class DemService {

    @Value("${dem.file-path}")
    private String demFilePath;

    private GridCoverage2D coverage;

    /**
     * 서버 시작 시 GeoTIFF DEM 파일을 한 번만 로드한다.
     * 매 요청마다 파일을 읽지 않으므로 성능에 영향 없다.
     */
    @PostConstruct
    public void init() {
        try {
            File demFile = new File(demFilePath);
            if (!demFile.exists()) {
                log.warn("DEM 파일을 찾을 수 없습니다. 경로={}", demFilePath);
                return;
            }
            GeoTiffReader reader = new GeoTiffReader(demFile);
            coverage = reader.read(null);
            log.info("DEM 파일 로드 완료. 경로={}", demFilePath);
        } catch (Exception e) {
            log.error("DEM 파일 로드 실패. 경로={}", demFilePath, e);
        }
    }

    /**
     * 스냅된 좌표 기준으로 DEM 고도를 샘플링한다.
     *
     * @param lat 위도 (snapped)
     * @param lon 경도 (snapped)
     * @return 고도(미터). DEM 범위 밖이거나 nodata면 null 반환
     */
    public Double getElevation(double lat, double lon) {
        if (coverage == null) {
            log.debug("DEM 커버리지 없음 - fallback 처리. lat={}, lon={}", lat, lon);
            return null;
        }

        try {
            DirectPosition position = new DirectPosition2D(
                    coverage.getCoordinateReferenceSystem(), lon, lat
            );

            // DEM 범위 밖 좌표 체크
            if (!coverage.getEnvelope().contains(position)) {
                log.debug("DEM 범위 밖 좌표. lat={}, lon={}", lat, lon);
                return null;
            }

            double[] result = new double[1];
            coverage.evaluate(position, result);

            double elevation = result[0];

            // nodata 값 체크 (-9999 또는 Float.NaN)
            if (Double.isNaN(elevation) || elevation <= -9000) {
                log.debug("DEM nodata 값. lat={}, lon={}", lat, lon);
                return null;
            }

            return elevation;

        } catch (Exception e) {
            log.debug("DEM 샘플링 실패. lat={}, lon={}, error={}", lat, lon, e.getMessage());
            return null;
        }
    }
}