package tunnel

import (
	"testing"

	"github.com/miekg/dns"
)

type fakeGameChecker map[string]bool

func (f fakeGameChecker) IsGameApp(app string) bool { return f[app] }

func TestBlockedAnswerTTLDependsOnGameMode(t *testing.T) {
	cases := []struct {
		name    string
		app     string
		games   GameAppChecker
		ttl     int // 0 = leave the default
		wantTTL uint32
	}{
		{name: "no checker", app: "com.game", wantTTL: defaultBlockedTTL},
		{name: "not a game", app: "com.mail", games: fakeGameChecker{"com.game": true}, wantTTL: defaultBlockedTTL},
		{name: "game default", app: "com.game", games: fakeGameChecker{"com.game": true}, wantTTL: DefaultGameBlockedTTL},
		{name: "game custom", app: "com.game", games: fakeGameChecker{"com.game": true}, ttl: 3, wantTTL: 3},
		{name: "game clamped low", app: "com.game", games: fakeGameChecker{"com.game": true}, ttl: -5, wantTTL: 1},
		{name: "game clamped high", app: "com.game", games: fakeGameChecker{"com.game": true}, ttl: 9999, wantTTL: 300},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			up := startUpstream(t, defaultUpstream)
			h := newTunHarness(t, up.addr)
			h.e.SetDomainChecker(&fakeChecker{custom: map[string]int{"ads.example": 1}})
			h.e.SetAppResolver(&fakeAppResolver{name: tc.app})
			if tc.games != nil {
				h.e.SetGameAppChecker(tc.games)
			}
			if tc.ttl != 0 {
				h.e.SetGameBlockedTTL(tc.ttl)
			}

			h.e.handleDNSQuery(queryInfo(t, "ads.example", dns.TypeA))
			m := h.mustRead(t)

			if len(m.Answer) != 1 || m.Answer[0].Header().Ttl != tc.wantTTL {
				t.Errorf("answer = %v, want one record with TTL %d", m.Answer, tc.wantTTL)
			}
			if up.count.Load() != 0 {
				t.Errorf("blocked query reached upstream")
			}
		})
	}
}
