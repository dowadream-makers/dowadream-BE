-- 시연용 가상 병원 (시안 06·07·08에 나오는 병원). is_demo = TRUE 로 실제 병원과 구분한다.
-- 좌표는 노원구청(기본 위치) 주변에 임의로 둔 값이고, 전화번호는 가짜 번호다.
INSERT INTO hospital (name, type_name, phone, address, lat, lng, doctor_total, specialist_total, avg_consult_min, is_demo) VALUES
    ('가상 바른정형외과의원', '의원', '02-000-0000', '가상 병원 (실제 주소 없음)', 37.6583, 127.0568, 2, 2, 7, TRUE),
    ('가상 튼튼정형외과의원', '의원', '02-000-0000', '가상 병원 (실제 주소 없음)', 37.6462, 127.0566, 1, 1, 5, TRUE),
    ('가상 함께내과의원',     '의원', '02-000-0000', '가상 병원 (실제 주소 없음)', 37.6543, 127.0650, 1, 1, 5, TRUE);

INSERT INTO hospital_department (hospital_id, dept_code, dept_name, specialist_count)
SELECT id, '05', '정형외과', specialist_total FROM hospital WHERE is_demo AND name LIKE '%정형외과%';
INSERT INTO hospital_department (hospital_id, dept_code, dept_name, specialist_count)
SELECT id, '01', '내과', specialist_total FROM hospital WHERE is_demo AND name LIKE '%내과%';
