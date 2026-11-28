package tunnel

// AppRuleAllowKeepSecurity is an AppDomainChecker result: let the domain through
// for this app (ad lists and custom rules are skipped) unless a security list
// blocks it. Game Mode uses it for its short "unblock" so a rewarded ad can load
// while malware and phishing domains stay blocked.
const AppRuleAllowKeepSecurity = 2

// securityMatches returns the IDs of the security lists that block domain.
func (e *Engine) securityMatches(domain string) []string {
	e.mu.Lock()
	secBlooms := e.secBlooms
	secTries := e.secTries
	secTrieIDs := e.secTrieIDs
	e.mu.Unlock()

	var ids []string
	for i, secTrie := range secTries {
		if secTrie == nil {
			continue
		}
		var secBloom *BloomFilter
		if i < len(secBlooms) {
			secBloom = secBlooms[i]
		}
		if secBloom != nil && !secBloom.MightContainDomainOrParent(domain) {
			continue
		}
		if secTrie.ContainsOrParent(domain) {
			id := "security"
			if i < len(secTrieIDs) {
				id = secTrieIDs[i]
			}
			ids = append(ids, id)
		}
	}
	return ids
}
