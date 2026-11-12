package kr.ject.naspi.league.domain;

/**
 * 리그 상태 (ERD: 생성 → 시작 → 종료).
 */
public enum LeagueStatus {

    /** 생성되어 대기실에서 참여자를 모으는 중. 초대 코드로 입장할 수 있다. */
    WAITING,

    /** 시작되어 거래가 진행 중. */
    STARTED,

    /** 종료되어 결과만 조회할 수 있다. */
    ENDED
}
