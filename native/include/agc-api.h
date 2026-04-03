// Vendored from https://github.com/refresh-bio/agc (v3.2, 2024-11-21)
// Original license: MIT
// Copyright(C) 2021-2024, S.Deorowicz, A.Danek, H.Li
//
// This file is kept as a reference for the JNA bindings in AgcLibrary.kt.
// Do NOT modify -- update by re-vendoring from upstream.
// *******************************************************************************************

#ifndef AGC_API_H
#define AGC_API_H

#ifdef __cplusplus
#include <vector>
#include <string>
#include <memory>

class CAGCFile
{
	std::unique_ptr<class CAGCDecompressorLibrary> agc;
	bool is_opened;

public:
	CAGCFile();
	~CAGCFile();

	bool Open(const std::string& file_name, bool prefetching = true);
	bool Close();
	int GetCtgLen(const std::string& sample, const std::string& name) const;
	int GetCtgSeq(const std::string& sample, const std::string& name, int start, int end, std::string& buffer) const;
	int NSample() const;
	int NCtg(const std::string& sample) const;
	int ListSample(std::vector<std::string>& samples) const;
	int GetReferenceSample(std::string& sample) const;
	int ListCtg(const std::string& sample, std::vector<std::string>& names) const;
};

typedef CAGCFile agc_t;
#define EXTERNC extern "C"
#else
typedef struct agc_t agc_t;
#define EXTERNC
#endif

// C API
EXTERNC agc_t* agc_open(char* fn, int prefetching);
EXTERNC int agc_close(agc_t* agc);
EXTERNC int agc_get_ctg_len(const agc_t *agc, const char *sample, const char *name);
EXTERNC int agc_get_ctg_seq(const agc_t *agc, const char *sample, const char *name, int start, int end, char *buf);
EXTERNC int agc_n_sample(const agc_t* agc);
EXTERNC int agc_n_ctg(const agc_t *agc, const char *sample);
EXTERNC char* agc_reference_sample(const agc_t* agc);
EXTERNC char **agc_list_sample(const agc_t *agc, int *n_sample);
EXTERNC char **agc_list_ctg(const agc_t *agc, const char *sample, int *n_ctg);
EXTERNC int agc_list_destroy(char **list);
EXTERNC int agc_string_destroy(char *sample);

#endif

// EOF
