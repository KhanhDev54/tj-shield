package tunnel

import (
	"testing"

	"github.com/miekg/dns"
)

type fakeAppRules map[string]int // domain -> result for every app

func (f fakeAppRules) CheckAppRule(domain, _ string) int {
	if v, ok := f[domain]; ok {
		return v
	}
	return -1
}

func TestAllowKeepSecurityStillBlocksSecurityDomains(t *testing.T) {
	adTrie, adBloom := compileTrie(t, "ads", "ads.example")
	secTrie, secBloom := compileTrie(t, "malware", "evil.example")
	rules := fakeAppRules{
		"ads.example":  AppRuleAllowKeepSecurity,
		"evil.example": AppRuleAllowKeepSecurity,
		"x.custom":     AppRuleAllowKeepSecurity,
	}
	cases := []struct {
		domain      string
		wantBlocked bool
	}{
		{domain: "ads.example", wantBlocked: false},
		{domain: "evil.example", wantBlocked: true},
		{domain: "x.custom", wantBlocked: false},
	}
	for _, tc := range cases {
		t.Run(tc.domain, func(t *testing.T) {
			up := startUpstream(t, defaultUpstream)
			h := newTunHarness(t, up.addr)
			h.e.SetTries(adTrie, secTrie, adBloom, secBloom)
			h.e.SetDomainChecker(&fakeChecker{custom: map[string]int{"x.custom": 1}})
			h.e.SetAppResolver(&fakeAppResolver{name: "com.game"})
			h.e.SetAppDomainChecker(rules)

			h.e.handleDNSQuery(queryInfo(t, tc.domain, dns.TypeA))
			m := h.mustRead(t)

			blocked := len(m.Answer) == 1 && m.Answer[0].(*dns.A).A.String() == "0.0.0.0"
			if blocked != tc.wantBlocked {
				t.Fatalf("blocked = %v, want %v (answer %v)", blocked, tc.wantBlocked, m.Answer)
			}
			wantUpstream := int64(1)
			if tc.wantBlocked {
				wantUpstream = 0
			}
			if got := up.count.Load(); got != wantUpstream {
				t.Errorf("upstream queries = %d, want %d", got, wantUpstream)
			}
		})
	}
}
