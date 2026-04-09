package com.orda.backend.domain.hiking.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import mil.nga.tiff.FileDirectory;
import mil.nga.tiff.Rasters;
import mil.nga.tiff.TIFFImage;
import mil.nga.tiff.TiffReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * GeoTIFF DEM 파일 기반 고도 샘플링 서비스
 *
 * 현재 구현 가정:
 * - EPSG:4326 (WGS84) 좌표계 기준
 * - ModelTiepointTag / ModelPixelScaleTag 메타데이터 필수
 * - NASADEM 한국 파일(korea_dem.tif) 기준으로 검증됨
 * - 경계 픽셀에서 Math.round 기반 인덱싱 사용 (소수점 오차 가능)
 */
@Slf4j
@Service
public class DemService {

    @Value("${dem.file-path}")
    private String demFilePath;

    private Rasters rasters;
    private double originLon;
    private double originLat;
    private double pixelWidth;
    private double pixelHeight;
    private int imageWidth;
    private int imageHeight;
    private boolean loaded = false;

    @PostConstruct
    public void init() {
        if (demFilePath == null || demFilePath.isBlank()) {
            log.info("DEM 파일 경로가 설정되지 않았습니다. canonical 고도는 null 처리됩니다.");
            return;
        }

        File demFile = new File(demFilePath);
        if (!demFile.exists()) {
            log.warn("DEM 파일을 찾을 수 없습니다. 경로={}", demFilePath);
            return;
        }

        try {
            TIFFImage tiffImage = TiffReader.readTiff(demFile);
            FileDirectory directory = tiffImage.getFileDirectory();

            rasters = directory.readRasters();
            imageWidth = rasters.getWidth();
            imageHeight = rasters.getHeight();

            List<Double> tiepoint = directory.getModelTiepoint();
            originLon = tiepoint.get(3);
            originLat = tiepoint.get(4);

            List<Double> pixelScale = directory.getModelPixelScale();
            pixelWidth = pixelScale.get(0);
            pixelHeight = pixelScale.get(1);

            loaded = true;
            log.info("DEM 파일 로드 완료. 경로={}, 해상도={}x{}", demFilePath, imageWidth, imageHeight);
        } catch (Exception e) {
            log.error("DEM 파일 로드 실패. 경로={}", demFilePath, e);
        }
    }

    /**
     * 스냅된 좌표 기준으로 DEM 고도를 샘플링한다.
     *
     * @param lat 위도 (snapped)
     * @param lon 경도 (snapped)
     * @return 고도(미터). DEM 미로드, 범위 밖, nodata면 null 반환
     */
    public Double getElevation(double lat, double lon) {
        if (!loaded) {
            log.debug("DEM 미로드 상태 - canonical 고도 없음. lat={}, lon={}", lat, lon);
            return null;
        }

        try {
            int pixelX = (int) Math.round((lon - originLon) / pixelWidth);
            int pixelY = (int) Math.round((originLat - lat) / pixelHeight);

            if (pixelX < 0 || pixelX >= imageWidth || pixelY < 0 || pixelY >= imageHeight) {
                log.debug("DEM 범위 밖 좌표. lat={}, lon={}", lat, lon);
                return null;
            }

            Number value = rasters.getPixel(pixelX, pixelY)[0];
            double elevation = value.doubleValue();

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