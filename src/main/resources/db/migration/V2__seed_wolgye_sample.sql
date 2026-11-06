-- 공공데이터 API 승인 전 임시 데이터: 월계동 병원 스프레드시트의 내과 예시 3곳.
-- 좌표(lat/lng)와 의사 수는 아직 없어서 비워 둡니다. 공공데이터 동기화가 생기면 채워집니다.
INSERT INTO hospital (name, type_name, phone, address) VALUES
    ('권내과의원',         '의원', '02-972-8375', '서울 노원구 마들로 111'),
    ('노원뉴하트내과의원', '의원', '02-913-7575', '서울 노원구 석계로 98-1'),
    ('위대한내과의원',     '의원', '02-6964-8200', '서울 노원구 화랑로 325');

INSERT INTO hospital_department (hospital_id, dept_code, dept_name)
SELECT id, '01', '내과' FROM hospital;
