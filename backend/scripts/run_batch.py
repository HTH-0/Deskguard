# -*- coding: utf-8 -*-
"""
dataset.csv를 읽어서 /api/chat 에 순회 호출하고, 결과를 새 CSV로 저장하는 배치 스크립트.
3단계 완료 기준에 있던 "손으로 100~200건 Postman 치지 않기" 위한 도구.
4·5·6·7단계에서 계속 재사용한다.

사용법:
    python run_batch.py --input ../../dataset.csv --output result.csv --base-url http://localhost:8080

주의: 서버가 먼저 떠 있어야 한다 (GEMINI_API_KEY 환경변수 설정 후 `mvn spring-boot:run`).
"""
import argparse
import csv
import time
import urllib.request
import json


def call_chat_api(base_url: str, prompt: str, timeout: int = 35) -> dict:
    """/api/chat 에 POST 요청을 보내고 결과를 dict로 돌려준다."""
    url = f"{base_url}/api/chat"
    body = json.dumps({"prompt": prompt}).encode("utf-8")
    req = urllib.request.Request(
        url, data=body, method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return {"status": "ok", "answer": data.get("answer", ""), "error": ""}
    except urllib.error.HTTPError as e:
        # 게이트웨이가 GlobalExceptionHandler로 내려주는 에러 JSON (또는 4단계부터는 차단 응답)
        try:
            data = json.loads(e.read().decode("utf-8"))
        except Exception:
            data = {"error": f"HTTP {e.code}", "message": str(e)}
        return {"status": "error", "answer": "", "error": json.dumps(data, ensure_ascii=False)}
    except Exception as e:
        return {"status": "error", "answer": "", "error": str(e)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="dataset.csv")
    parser.add_argument("--output", default="batch_result.csv")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--delay", type=float, default=1.0, help="요청 사이 대기 시간(초) - 무료 티어 한도 보호용")
    args = parser.parse_args()

    with open(args.input, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    fieldnames = list(rows[0].keys()) + ["result_status", "result_answer", "result_error"]
    with open(args.output, "w", newline="", encoding="utf-8-sig") as out_f:
        writer = csv.DictWriter(out_f, fieldnames=fieldnames)
        writer.writeheader()

        for i, row in enumerate(rows, 1):
            result = call_chat_api(args.base_url, row["prompt"])
            row["result_status"] = result["status"]
            row["result_answer"] = result["answer"]
            row["result_error"] = result["error"]
            writer.writerow(row)
            print(f"[{i}/{len(rows)}] {result['status']} - {row['prompt'][:40]}")
            time.sleep(args.delay)

    print(f"완료: {args.output} 에 저장됨")


if __name__ == "__main__":
    main()
