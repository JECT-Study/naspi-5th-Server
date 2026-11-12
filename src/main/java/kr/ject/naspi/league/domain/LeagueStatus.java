package kr.ject.naspi.league.domain;

/**
 * 리그 상태 (ERD: 생성 → 시작 → 종료, 생성 → 만료).
 */
public enum LeagueStatus {

    /** 생성되어 대기실에서 참여자를 모으는 중. 초대 코드로 입장할 수 있다. */
    WAITING,

    /** 시작되어 거래가 진행 중. */
    STARTED,

    /** 종료되어 결과만 조회할 수 있다. */
    ENDED,

    /** 생성 후 7일 안에 시작하지 않아 만료된 대기실. 접근할 수 없고 초대 코드도 폐기된다. */
    EXPIRED
}
