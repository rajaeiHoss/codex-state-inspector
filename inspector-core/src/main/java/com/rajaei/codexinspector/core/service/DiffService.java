package com.rajaei.codexinspector.core.service;

import com.rajaei.codexinspector.core.domain.DiffResult;
import com.rajaei.codexinspector.core.domain.Snapshot;

public interface DiffService {
    DiffResult diff(Snapshot before, Snapshot after);
}
