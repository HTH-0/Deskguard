@AGENTS.md

# 이 저장소에서 시작할 때

0. 브랜치는 `main` 하나만 사용한다(2026-10-30 정리, 클라우드 작업을 쓰지 않는다). `git fetch origin --prune` 후 `git switch main`, `git pull`로 최신 상태를 받는다. 두 컴퓨터에서 같은 작업을 따로 한 일이 있었으므로, 작업을 시작하기 전에 `main`이 원격과 일치하는지 확인하고 `git branch -r`에 다른 브랜치가 없는지 본다. 다른 브랜치가 보이면 그 브랜치의 `docs/handoff.md`와 비교해서 사용자에게 알린다.
1. 먼저 [docs/handoff.md](docs/handoff.md)를 읽는다. 현재 상태, 사용자 결정 대기 목록, 다음 작업이 있다.
2. 작업 규칙(한국어 응답, 계획 후 승인, 작업 스텝 보고)은 위에서 가져온 AGENTS.md를 따른다.
3. 결정이 생기면 그 자리에서 DECISIONS.md에 기록한다.
